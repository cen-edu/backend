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
                CURRENT_REQUEST_JSON:\n%s%s
                """.formatted(request, repair);
    }

    public List<ChatMessage> messages(ProblemGenerationCommand command) {
        List<ChatMessage> messages = new ArrayList<>();
        if (!command.references().isEmpty()) messages.add(ChatMessage.user("FEW_SHOT_JSON\n" + references.serialize(command.curriculum(), command.references())));
        messages.add(ChatMessage.user("GENERATE_SEMANTIC_MODEL"));
        return List.copyOf(messages);
    }
}
