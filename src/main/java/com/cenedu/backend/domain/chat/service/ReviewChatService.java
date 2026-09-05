package com.cenedu.backend.domain.chat.service;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.dispatcher.AgentDispatcher;
import com.cenedu.backend.domain.chat.dto.request.ReviewChatRequest;
import com.cenedu.backend.domain.chat.dto.response.ChatResponse;
import com.cenedu.backend.domain.worksheet.service.StudentResultQueryService;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.UserRole;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 공개된 본인 결과의 단일 문항만 해설 에이전트로 전달한다. LLM 호출 동안 DB 트랜잭션을 유지하지 않는다. */
@Service
public class ReviewChatService {
    private final StudentResultQueryService results;
    private final AgentDispatcher dispatcher;
    private final ObjectMapper mapper;
    private final int maxLength;

    public ReviewChatService(StudentResultQueryService results, AgentDispatcher dispatcher,
            ObjectMapper mapper, @Value("${app.ai.guard.input.max-length}") int maxLength) {
        this.results = results;
        this.dispatcher = dispatcher;
        this.mapper = mapper;
        this.maxLength = maxLength;
    }

    /** 소유권·공개 여부·문항 소속 확인 후 해설 질문을 처리한다. */
    public ChatResponse answer(long memberId, UserRole role, long assignmentStudentId,
            long worksheetItemId, ReviewChatRequest request) {
        if (role != UserRole.STUDENT) throw new BusinessException(ErrorCode.FORBIDDEN);
        String question = request.question();
        if (question == null || question.isBlank()) throw new BusinessException(ErrorCode.CHAT_QUESTION_BLANK);
        if (question.codePointCount(0, question.length()) > maxLength) {
            throw new BusinessException(ErrorCode.CHAT_QUESTION_TOO_LONG);
        }
        var history = ChatService.toHistory(request.historyOrEmpty());
        var result = results.getResult(memberId, assignmentStudentId);
        var item = result.items().stream().filter(i -> i.worksheetItemId() == worksheetItemId)
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_ITEM_NOT_FOUND));
        // 미제출자는 공개 전에도 결과 DTO가 반환될 수 있다. 공개 게이트가 만든 chatContext를 반드시 검사한다.
        if (item.chatContext() == null) throw new BusinessException(ErrorCode.WORKSHEET_RESULT_NOT_RELEASED);
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("worksheetItemId", item.worksheetItemId());
        material.put("questionId", item.questionId());
        material.put("format", item.format());
        material.put("contentBlocks", item.contentBlocks());
        material.put("choices", item.choices());
        material.put("steps", item.steps());
        material.put("explanation", item.explanation());
        // 필기 URL/스토리지 주소/전체 학습지/타인 결과는 모델에 전달하지 않는다.
        material.put("answerUnits", item.answerUnits().stream().map(unit -> {
            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("answerUnitId", unit.answerUnitId());
            answer.put("displayOrder", unit.displayOrder());
            answer.put("myAnswer", unit.myAnswer());
            answer.put("correctAnswer", unit.correctAnswer());
            answer.put("selectedChoiceId", unit.selectedChoiceId());
            answer.put("correctChoiceId", unit.correctChoiceId());
            return answer;
        }).toList());
        var response = dispatcher.dispatch(new AgentRequest(AgentKind.REVIEW_CHAT,
                new Actor(memberId, Actor.Role.STUDENT), question, history,
                Map.of("reviewMaterial", mapper.writeValueAsString(material))));
        return new ChatResponse(response.text(), null);
    }
}
