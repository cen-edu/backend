package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Component;
import com.cenedu.backend.ai.problem.adapter.FewShotReferenceSerializer;

@Component
public final class ProblemSemanticGenerationPromptFactory {
    private final ObjectMapper mapper = new ObjectMapper(); private final FewShotReferenceSerializer references;
    public ProblemSemanticGenerationPromptFactory(FewShotReferenceSerializer references) { this.references = references; }

    public String create(ProblemGenerationCommand command, List<String> repairFindings) {
        String request;
        try {
            Map<String, Object> requestData = new LinkedHashMap<>();
            requestData.put("purpose", command.purpose());
            requestData.put("specification", command.specification());
            requestData.put("curriculum", command.curriculum());
            if (command.personalizedEvidence() != null) {
                requestData.put("personalizedEvidence", command.personalizedEvidence());
            }
            command.references().stream().filter(r -> r.role() == com.cenedu.backend.domain.problem.authoring.generation.GenerationReferenceRole.ORIGIN)
                    .findFirst().ifPresent(origin -> requestData.put("originVisual", originVisual(origin)));
            request = mapper.writeValueAsString(requestData);
        }
        catch (Exception e) { throw new IllegalStateException("semantic generation request를 만들 수 없습니다.", e); }
        String repair = repairFindings == null || repairFindings.isEmpty() ? "" : "\nREPAIR_FINDINGS\n" + repairFindings.stream().limit(10).map(x -> x.length() > 200 ? x.substring(0, 200) : x).toList();
        return """
                당신은 2022 개정 중학교 1학년 수학 문제의 semantic model 생성기다.
                반드시 SEMANTIC_MODEL_V1 JSON 객체만 출력하고 Markdown을 사용하지 마라.
                schemaVersion은 1이어야 하며, server가 제공한 curriculum 범위만 사용하라.
                parameters와 computations의 key는 대문자 논리 키를 사용하고 모든 목록은 null 대신 []를 사용하라.
                직접 복사한 참고 문제, 지원하지 않는 operation, free-form SVG, 범위 밖 교육 내용을 만들지 마라.
                questionTemplate과 explanationTemplate은 실제 값이 삽입될 수 있는 템플릿이어야 한다.
                visualRequirement.mode가 AUTO이면 구체적인 시각 대상이 없거나 자산 없이 정답을 결정할 수 없는 경우에만
                diagram을 정확히 하나 생성하고, 본문에 그림 정보를 전부 중복하지 마라. 조건을 만족하지 않으면
                visualRequired=false와 diagrams=[]를 출력하라.
                visualRequirement.mode가 PRESERVE_ORIGIN이면 originVisual의 visualKind와 diagram 구조를 유지하라.
                최종적으로 학생에게 표시되는 문제·풀이·해설의 수식은 인라인 LaTeX $...$ 형식을 사용하고,
                비교용 정답 값에는 $, $$, \\(, \\) 구분자를 넣지 마라.
                CURRENT_REQUEST_JSON:\n%s%s
                """.formatted(request, repair);
    }

    private Object originVisual(com.cenedu.backend.domain.problem.authoring.generation.GenerationReference origin) {
        if (origin.visualReference() != null) {
            var descriptor = origin.visualReference();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("visualKind", descriptor.kind().name());
            result.put("visualAssetKey", descriptor.assetKey());
            result.put("altText", descriptor.altText());
            result.put("diagram", descriptor.diagramSpec());
            result.put("directCopyForbidden", true);
            result.put("preserveKind", true);
            result.put("changeValuesForSimilarOrIncreaseReasoningForApplication", true);
            return result;
        }
        if (origin.semanticModel() == null) return Map.of("visualKind", "UNKNOWN_FIGURE", "directCopyForbidden", true);
        return Map.of("visualKind", origin.semanticModel().diagrams().isEmpty() ? "NONE" : origin.semanticModel().diagrams().get(0).kind().name(),
                "semanticModel", origin.semanticModel(), "directCopyForbidden", true);
    }

    public List<ChatMessage> messages(ProblemGenerationCommand command) {
        List<ChatMessage> messages = new ArrayList<>();
        if (!command.references().isEmpty()) messages.add(ChatMessage.user("FEW_SHOT_JSON\n" + references.serialize(command.curriculum(), command.references())));
        messages.add(ChatMessage.user("GENERATE_SEMANTIC_MODEL"));
        return List.copyOf(messages);
    }
}
