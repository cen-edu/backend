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
            if (command.editInstruction() != null && !command.editInstruction().isBlank()) {
                requestData.put("editInstruction", command.editInstruction());
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
                questionTemplate, explanationTemplate, contentTemplate, learningGuide 템플릿의 값 치환은 반드시 {{KEY}} 또는 {{KEY_UNIT}} 문법만 사용하라.
                ${KEY}, {{ key }}, 자연어 대괄호 등 다른 placeholder 문법은 사용하지 마라. LaTeX의 $...$ 구분자는 그대로 유지하라.
                intent.targetKey는 반드시 computations 또는 parameters에 실제로 정의된 key 하나를 그대로 사용하라.
                targetKey를 설명 문장이나 존재하지 않는 key로 만들지 마라.
                모든 diagram의 style은 stroke/fill/accent를 대문자 또는 소문자 6자리 hex 색상(#RRGGBB)으로,
                strokeWidth는 1~8 정수, fontFamily는 정확히 sans-serif, fontSize는 10~32 정수로 출력하라.
                presentation.learningGuide는 반드시 null이 아닌 객체로 출력하고 conceptTitleTemplate, summaryTemplate,
                keyPointTemplates(1~3개)를 포함하라.
                learningGuide의 conceptTitle, summary, keyPoints에는 정답 보기의 내용, 정답 식, 최종 비례상수나 계산 결과를 넣지 마라.
                학습 안내는 개념과 풀이 절차만 설명하고 이 문항의 수치·최종 식·정답 선택지를 직접 제시하지 마라.
                explanationTemplate에서 좌표 그래프의 점을 근거로 들 때에도 (x,y) 좌표를 그대로 재인용하지 말고,
                그래프의 점을 읽어 비례상수나 관계를 판단하는 일반적인 절차로 설명하라.
                객관식 choices의 choiceKey는 C1, C2처럼 대문자 C와 1부터 시작하는 번호를 사용하고,
                displayOrder는 0부터 중복 없이 연속된 정수로 출력하라.
                객관식의 모든 choice.valueKey는 null이 아니며 parameters 또는 computations에 실제로 정의된
                서로 다른 논리 key를 참조해야 한다.
                diagram assetKey는 F1, F2처럼 대문자 F와 1부터 시작하는 번호만 사용하라.
                현재 생성 자산은 문항 본문의 하나의 시각 자료다. 보기마다 별도의 그림을 만들지 말고,
                객관식 보기는 실제 수식·값·판단 문장으로 작성하라. 그래프나 표를 설명하는 문장을 보기 내용으로 복사하지 마라.
                COORDINATE_GRAPH는 축 범위·눈금·점 좌표·함수 또는 선분 정보를 실제로 표시할 수 있게 모두 제공하라.
                그림의 라벨은 학생이 관찰할 수 있는 표식만 담아야 한다. 발문이 식·계수·관계·그래프 유형·값을 묻는 경우
                그 정답이나 정답 보기의 수식을 function/line/segment/point labelTemplate에 직접 넣지 마라.
                해당 정보는 축·눈금·점·선의 모양처럼 학생이 판단에 사용할 관찰 정보로 표현하고, 정답을 쓰게 되는 라벨은 빈 문자열로 둬라.
                예를 들어 y=2x를 고르는 문제에서 function.labelTemplate에 y=2x를 넣지 마라.
                DATA_TABLE은 모든 행·열 제목과 셀 값을 diagram spec에 제공하라.
                직접 복사한 참고 문제, 지원하지 않는 operation, free-form SVG, 범위 밖 교육 내용을 만들지 마라.
                questionTemplate과 explanationTemplate은 실제 값이 삽입될 수 있는 {{KEY}} 템플릿이어야 한다.
                visualRequirement.mode가 REQUIRED이면 반드시 visualRequired=true로 설정하고 허용된 diagram을 정확히 하나 생성하라.
                visualRequirement.mode가 AUTO이면 구체적인 시각 대상이 없거나 자산 없이 정답을 결정할 수 없는 경우에만
                diagram을 정확히 하나 생성하고, 본문에 그림 정보를 전부 중복하지 마라. 조건을 만족하지 않으면
                visualRequired=false와 diagrams=[]를 출력하라.
                visualRequirement.mode가 PRESERVE_ORIGIN이면 originVisual의 visualKind와 diagram 구조를 유지하라.
                CURRENT_REQUEST_JSON에 editInstruction이 있으면 이건 임의로 비슷한 문제를 만들라는 뜻이
                아니라 교사가 origin 문제에 실제로 요청한 구체적인 변경이다. origin의 구조와 스타일은
                유지하되 editInstruction이 요구하는 값·조건을 정확히 반영해서 다시 만들어라.
                editInstruction이 없으면 origin과 비슷하거나 조금 더 어려운 변형을 만들어라.
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
