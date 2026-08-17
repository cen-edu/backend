package com.cenedu.backend.domain.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.AgentResponse;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.dispatcher.AgentDispatcher;
import com.cenedu.backend.domain.chat.dto.request.ChatHistoryMessage;
import com.cenedu.backend.domain.chat.dto.request.ChatRequest;
import com.cenedu.backend.domain.chat.dto.response.ChatResponse;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.UserRole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 개념 챗봇 한 턴을 처리한다. <b>로직은 파이프라인에 없고 계약에 있다.</b>
 *
 * <p>이 서비스가 하는 일은 셋뿐이다 — 클라이언트가 보낸 것을 검증하고, {@code AgentDispatcher}
 * 가 받을 모양으로 조립하고, 다음 턴에 되돌려줄 앵커를 응답에서 꺼낸다.
 *
 * <p><b>{@code AgentDispatcher} 를 반드시 거친다.</b> 엔진을 직접 부르면 입력 가드(역할·길이·
 * 프롬프트 인젝션)를 통째로 건너뛴다. ArchUnit 규칙 "도메인은 사용자 프롬프트 에이전트를 직접
 * 호출하지 않는다" 가 이것을 CI 에서 강제한다.
 *
 * <pre>
 * Controller → ChatService → AgentDispatcher → SolveChatAgent → ConceptChatEngine
 * </pre>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /**
     * 서버가 거는 이력 개수 상한. <b>넘으면 오래된 쪽부터 버린다</b> — 자르는 것이 400 보다 낫다.
     * 클라이언트가 대화를 쌓아 갈수록 이력이 무한히 길어지는데, 그때 요청을 거절하면 대화가
     * 어느 순간부터 아예 안 되고 학생은 이유를 알 수 없다.
     *
     * <p>엔진이 키워드 추출에 최근 6개만 쓰더라도 이 상한이 필요하다 — <b>2차 생성 호출에는
     * 이력 전체가 실린다.</b> 요청 크기와 생성 토큰이 그대로 늘어난다.
     */
    public static final int MAX_HISTORY = 20;

    /**
     * 직전 턴의 앵커를 주고받는 payload 키.
     *
     * <p><b>문자열을 새로 적지 않고 엔진이 쓰는 상수를 그대로 재사용한다</b>(task_25 §3-4).
     * 양쪽에 따로 적으면 한쪽만 고쳤을 때 되감김으로 조용히 돌아간다 — 오류도 로그도 없이
     * 기능만 사라지는 종류의 실패다.
     *
     * <p>이 상수는 컴파일 시점에 값이 인라인되므로 바이트코드에 {@code ai.chat.agent} 참조가
     * 남지 않는다. ArchUnit 의 "도메인은 사용자 프롬프트 에이전트를 직접 호출하지 않는다" 규칙은
     * <b>호출</b>을 막는 것이고 여기서 부르는 것은 없다. 규칙이 실제로 통과하는지는 §4 에서 확인한다.
     */
    private static final String PAYLOAD_CURRENT_CONCEPT_ID =
            com.cenedu.backend.ai.chat.agent.ConceptChatEngine.PAYLOAD_CURRENT_CONCEPT_ID;

    /** 소단원 개념 목록을 추출 프롬프트에 넣을 때 쓰는 키. 없으면 목록 없이 진행한다. */
    private static final String PAYLOAD_SUB_UNIT_ID = "subUnitId";

    private final AgentDispatcher agentDispatcher;
    private final ConceptQueryService conceptQueryService;

    /**
     * 질문 길이 상한. <b>가드와 같은 설정값을 읽는다</b> — 여기서 먼저 막지 않으면
     * {@code InputLengthGuard} 가 {@code AI_REQUEST_BLOCKED} 로 막아 챗봇 고유의 오류 코드가
     * 나가지 않는다. 두 곳이 다른 값을 보면 안 되므로 상수를 새로 두지 않고 같은 프로퍼티를 읽는다.
     */
    private final int maxQuestionLength;

    public ChatService(
            AgentDispatcher agentDispatcher,
            ConceptQueryService conceptQueryService,
            @Value("${app.ai.guard.input.max-length}") int maxQuestionLength
    ) {
        this.agentDispatcher = agentDispatcher;
        this.conceptQueryService = conceptQueryService;
        this.maxQuestionLength = maxQuestionLength;
    }

    public ChatResponse answer(long memberId, UserRole role, ChatRequest request) {
        String question = validateQuestion(request.question());
        List<ChatMessage> history = toHistory(request.historyOrEmpty());

        AgentResponse response = agentDispatcher.dispatch(new AgentRequest(
                AgentKind.SOLVE_CHAT,
                new Actor(memberId, actorRole(role)),
                question,
                history,
                payload(request)));

        return new ChatResponse(response.text(), carriedConceptId(response));
    }

    /**
     * 길이는 코드 포인트로 센다. {@code InputLengthGuard} 가 같은 방식으로 세므로 여기서
     * UTF-16 코드 유닛으로 세면 이모지가 든 질문에서 두 판정이 갈린다.
     */
    private String validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.CHAT_QUESTION_BLANK);
        }
        if (question.codePointCount(0, question.length()) > maxQuestionLength) {
            throw new BusinessException(ErrorCode.CHAT_QUESTION_TOO_LONG);
        }
        return question;
    }

    /**
     * 이력을 엔진이 받는 모양으로 옮긴다. <b>상한을 넘으면 오래된 쪽부터 버린다.</b>
     *
     * <p>{@code role} 은 대소문자를 가리지 않는다 — 프론트가 {@code "user"} 를 보낼지
     * {@code "USER"} 를 보낼지는 계약 밖의 취향이고, 여기서 조용히 틀리게 하는 것보다
     * 받아 주는 편이 낫다. 그 둘이 아닌 값은 오타이므로 막는다.
     */
    private static List<ChatMessage> toHistory(List<ChatHistoryMessage> messages) {
        List<ChatHistoryMessage> kept = messages.size() <= MAX_HISTORY
                ? messages
                : messages.subList(messages.size() - MAX_HISTORY, messages.size());
        if (kept.size() < messages.size()) {
            log.info("개념 챗봇 이력 절단 — received={}, kept={}", messages.size(), kept.size());
        }

        List<ChatMessage> history = new ArrayList<>(kept.size());
        for (ChatHistoryMessage message : kept) {
            if (message == null || message.role() == null || message.content() == null
                    || message.content().isBlank()) {
                throw new BusinessException(ErrorCode.CHAT_HISTORY_INVALID);
            }
            history.add(new ChatMessage(role(message.role()), message.content()));
        }
        return history;
    }

    private static ChatMessage.Role role(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case ChatHistoryMessage.ROLE_USER -> ChatMessage.Role.USER;
            case ChatHistoryMessage.ROLE_ASSISTANT -> ChatMessage.Role.ASSISTANT;
            default -> throw new BusinessException(ErrorCode.CHAT_HISTORY_INVALID);
        };
    }

    /**
     * <b>없는 id 는 오류가 아니라 없는 것으로 친다.</b> 클라이언트가 오래된 값을 들고 있을 수
     * 있고, 그때 400 을 내면 학생 화면에서는 대화가 그냥 끊긴 것으로 보인다. 앵커를 버리고
     * 키워드로 새로 찾으면 대화는 이어진다.
     *
     * <p>{@code subUnitId} 는 존재 검사를 하지 않는다 — 없는 id 면 소단원 개념 목록 조회가
     * 빈 목록을 돌려주고 파이프라인이 목록 없이 진행한다. 검사를 더해도 결과가 같다.
     */
    private Map<String, Object> payload(ChatRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.subUnitId() != null) {
            payload.put(PAYLOAD_SUB_UNIT_ID, request.subUnitId());
        }
        if (request.currentConceptId() != null) {
            if (conceptQueryService.findConcept(request.currentConceptId()).isPresent()) {
                payload.put(PAYLOAD_CURRENT_CONCEPT_ID, request.currentConceptId());
            } else {
                log.info("개념 챗봇 앵커 무시 — 존재하지 않는 conceptId={}", request.currentConceptId());
            }
        }
        return payload;
    }

    /**
     * 다음 턴에 되돌려줄 앵커를 응답에서 꺼낸다. <b>값이 없거나 형이 다르면 {@code null} 이다</b> —
     * 여기서 예외를 던지면 답변이 멀쩡히 생성됐는데 응답이 실패로 바뀐다.
     */
    private static Long carriedConceptId(AgentResponse response) {
        return response.data().get(PAYLOAD_CURRENT_CONCEPT_ID) instanceof Number number
                ? number.longValue()
                : null;
    }

    /** {@code global} 의 사용자 역할을 에이전트 역할로 옮긴다. 값이 늘면 여기서 컴파일이 깨진다. */
    private static Actor.Role actorRole(UserRole role) {
        return switch (role) {
            case STUDENT -> Actor.Role.STUDENT;
            case TEACHER -> Actor.Role.TEACHER;
        };
    }
}
