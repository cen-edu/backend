package com.cenedu.backend.ai.analysis.adapter;

import java.util.ArrayList;
import java.util.List;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.ai.client.LlmUseCase;
import com.cenedu.backend.ai.client.OpenAiProperties;
import com.cenedu.backend.domain.analysis.report.AnalysisReportDraft;
import com.cenedu.backend.domain.analysis.report.AnalysisReportGenerationPort;
import com.cenedu.backend.domain.analysis.report.AnalysisReportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 분석 문장 생성 어댑터.
 *
 * <p>교사가 프롬프트를 직접 입력하지 않는 시스템 트리거 경로라 {@code AgentDispatcher} 를 거치지
 * 않고 도메인 Port 를 구현한다(AGENTS.md 3절 4번).
 *
 * <p>학생 답안을 프롬프트 문장에 이어 붙이지 않고 <b>JSON 값으로</b> 넘긴다. 붙여 쓰면 학생이 쓴
 * 문장과 우리가 쓴 지시가 같은 평면에 놓여 구분이 사라진다.
 *
 * <p>모델은 기본값과 같지만 {@link LlmUseCase#ANALYSIS_REPORT} 로 부른다. 문항마다 문장 세 개를
 * 한 번에 받아 출력이 길어서, 응답 상한을 따로 잡지 않으면 문항이 많은 학습지에서 JSON 이 잘린다.
 *
 * <p>seed 를 고정하지 않는다. 검증기와 달리 문장 생성은 같은 입력에 같은 답이 나올 필요가 없고,
 * 재생성이 조금씩 다른 표현을 내는 편이 교사에게 자연스럽다.
 */
@Component
public class AnalysisReportGenerator implements AnalysisReportGenerationPort {

    private static final Logger log = LoggerFactory.getLogger(AnalysisReportGenerator.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;

    public AnalysisReportGenerator(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            OpenAiProperties properties
    ) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AnalysisReportDraft generate(AnalysisReportRequest request) {
        String requestJson = objectMapper.writeValueAsString(request);
        LlmResponse response = llmClient.complete(
                AnalysisReportPrompts.systemPrompt(),
                List.of(ChatMessage.user(AnalysisReportPrompts.userPrompt(requestJson))),
                null,
                LlmUseCase.ANALYSIS_REPORT);

        // 출력 길이가 한도에 닿으면 JSON 이 잘려 파싱이 깨진다. 한도를 올릴지 판단할 근거로 남긴다.
        log.info("분석 문장 생성 호출 — assignmentStudentId={}, 문항 {}건, "
                        + "prompt={} tokens, completion={} tokens",
                request.assignmentStudentId(),
                request.gradedItems().size(),
                response.promptTokens(),
                response.completionTokens());

        JsonNode root = parse(response.text());
        return new AnalysisReportDraft(
                text(root, "summaryMessage"),
                text(root, "overallObservation"),
                itemMessages(root),
                AnalysisReportPrompts.VERSION,
                properties.optionsFor(LlmUseCase.ANALYSIS_REPORT).model(),
                schemaVersion(root));
    }

    private List<AnalysisReportDraft.ItemMessageDraft> itemMessages(JsonNode root) {
        JsonNode itemMessages = root.path("itemMessages");
        if (!itemMessages.isArray()) {
            throw new AnalysisReportResponseParseException("응답의 itemMessages 가 배열이 아닙니다.");
        }
        List<AnalysisReportDraft.ItemMessageDraft> drafts = new ArrayList<>();
        for (JsonNode item : itemMessages) {
            JsonNode itemId = item.path("worksheetItemId");
            if (!itemId.isNumber()) {
                throw new AnalysisReportResponseParseException(
                        "응답의 itemMessages 원소에 worksheetItemId 가 없습니다.");
            }
            drafts.add(new AnalysisReportDraft.ItemMessageDraft(
                    itemId.asLong(),
                    text(item, "observation"),
                    text(item, "learningPoint"),
                    text(item, "retryGuide")));
        }
        return drafts;
    }

    /**
     * 평문에서 JSON 을 꺼낸다. {@code LlmClient} 는 구조화 출력 경로가 아니라 String 만 돌려준다.
     *
     * <p>코드 펜스는 벗겨 낸다. 프롬프트로 금지해도 붙어 오는 경우가 있어서다. 그 밖의 교정은 하지
     * 않는다 — 응답을 억지로 살리면 모델이 실제로 무엇을 냈는지 알 수 없게 된다.
     */
    private JsonNode parse(String text) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(text));
            if (root == null || !root.isObject()) {
                throw new AnalysisReportResponseParseException("응답이 JSON 객체가 아닙니다.");
            }
            return root;
        } catch (JacksonException e) {
            throw new AnalysisReportResponseParseException("응답을 JSON 으로 읽지 못했습니다.", e);
        }
    }

    private static String stripCodeFence(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int closing = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || closing <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, closing).strip();
    }

    /** 값이 없으면 null 을 돌려준다. 공백 여부 판단은 도메인 검증기가 한다. */
    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asString() : null;
    }

    private Short schemaVersion(JsonNode root) {
        JsonNode value = root.path("schemaVersion");
        return value.isNumber() ? (short) value.asInt() : null;
    }
}
