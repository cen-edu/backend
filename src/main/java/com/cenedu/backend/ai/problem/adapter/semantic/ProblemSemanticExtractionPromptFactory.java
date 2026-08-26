package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProblemSemanticExtractionPromptFactory {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 원본 문항을 semantic model로 되돌리는 규칙을 문항 유형별로 지시한다.
     *
     * <p>steps·rubrics를 채우는 방법을 적는 이유는 materializer가 이 값으로 빈칸형 단계와
     * 서술형 채점 기준을 복원하기 때문이다. 비어 있으면 그 유형은 semantic 수정을 아예 쓸 수
     * 없고, 원본에 없는 값을 지어내면 교사가 값 하나만 바꿔도 원본에 없던 단계나 채점 기준이
     * 문항에 생겨난다. 둘 다 막아야 하므로 "원본에서 그대로 옮기고, 없으면 비워 둔다"를
     * 명시한다.
     */
    public String systemPrompt() {
        return """
                기존 수학 문제를 semantic model v1로 구조화하라. 원본의 정답과 의미를 보존하고
                지원하지 않는 도형·연산은 임의로 추정하지 말라. JSON만 출력하라.

                구조는 SOURCE_SNAPSHOT_JSON에 있는 것만 옮긴다. 원본에 없는 보기·단계·채점 기준을
                새로 만들지 말고, 없으면 해당 배열을 비워 둔다. 개수가 원본과 다르면 그 추출은 버려진다.

                presentation.steps는 원본 steps를 그대로 옮긴다. 각 단계의 segments는 원본 순서를
                유지하고, BLANK segment마다 valueKey에 그 빈칸의 정답에 해당하는 parameter 또는
                computation 키를 넣는다. compareMethod와 diagnosticType도 원본 answerUnits에서 그대로
                가져온다. 앞선 빈칸의 값을 다시 보여 주는 자리는 ANSWER_REF로 두고 그 빈칸과 같은
                unitKey를 쓴다. 빈칸형이 아니면 steps는 빈 배열이다.

                presentation.rubrics는 원본 rubricItems를 그대로 옮기고 weightPercent 합이 100이
                되게 한다. 원본에 채점 기준이 없으면 지어내지 말고 빈 배열로 둔다. 서술형이 아니면
                rubrics는 빈 배열이다.

                presentation.choices는 객관식일 때만 채우고, 각 보기의 valueKey에 그 보기가 나타내는
                값의 키를 넣는다. 정답 보기의 값은 intent.targetKey의 값과 같아야 한다.

                parameters의 editable은 "교사가 이 값을 바꿔도 문항이 계속 성립하는가"를 뜻한다.
                editable=true로 둘 것: 문제에서 주어진 독립 입력값. 반지름 3cm의 3, 2 + 3의 2와 3처럼
                교사가 다른 값으로 바꾸면 정답·보기·해설이 computations로 다시 계산되어 따라오는 값이다.
                editable=false로 둘 것: 오답 보기 전용 값처럼 혼자 바꾸면 정답과 충돌할 수 있는 값,
                도형의 축 범위·눈금 간격처럼 표현을 위한 값, 그리고 다른 값에서 유도되는 값.
                유도되는 값은 parameters가 아니라 computations로 표현한다.
                모든 parameter를 editable=false로 두지 말라. 그러면 교사가 숫자 하나도 바꿀 수 없고
                모든 수정이 문항 재생성으로 처리된다. 문제의 조건에 해당하는 값은 편집할 수 있어야 한다.

                editable parameter와 계산 결과를 presentation에 숫자 문자열로 고정하지 말라.
                원본 문항에 보이는 값은 questionTemplate·contentTemplate·explanationTemplate·step
                template에 {{INITIAL_TEMP}}, {{EVENING_TEMP}}, {{INITIAL_TEMP_UNIT}}처럼 해당 key의
                placeholder로 대체하라. 객관식 보기의 contentTemplate은 valueKey가 나타내는 값을
                placeholder로 참조해야 한다. 예를 들어 valueKey가 EVENING_TEMP이면
                {{EVENING_TEMP}}를 쓴다. diagram·table의 값은 문자열 숫자가 아니라
                구조화된 key 필드로 참조하라. editable parameter를 바꿨을 때 문제 조건과
                정답·보기·해설·도식이 같은 계산 그래프를 통해 다시 물질화될 수 있어야 한다.

                parameter key는 반드시 영문 대문자로 시작하고 영문 대문자·숫자·밑줄만 사용한다.
                예: RADIUS, LEFT_VALUE, X1. 한글·소문자·공백은 사용하지 않는다.
                bounds는 INTEGER·DECIMAL·RATIONAL처럼 숫자로 해석 가능한 parameter에만 사용한다.
                minInclusive와 maxInclusive는 숫자 문자열이어야 하고 min <= 현재 value <= max를 만족해야 한다.
                안전한 범위를 확정할 수 없거나 TEXT·POINT·BOOLEAN 값이면 bounds를 null로 둔다.
                """;
    }

    public List<ChatMessage> messages(SemanticExtractionCommand command) {
        try {
            return List.of(ChatMessage.user("SOURCE_SNAPSHOT_JSON\n"
                    + mapper.writeValueAsString(Map.of("curriculum", command.curriculum(),
                    "snapshot", command.snapshot()))));
        } catch (Exception e) {
            throw new IllegalArgumentException("extraction prompt를 만들 수 없습니다.", e);
        }
    }

    /** 첫 추출의 domain validation 위반만 덧붙여 전체 semantic JSON을 한 번 교정하게 한다. */
    public List<ChatMessage> correctionMessages(SemanticExtractionCommand command, String finding) {
        var result = new java.util.ArrayList<>(messages(command));
        String normalized = finding == null ? "semantic validation 실패"
                : finding.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 500) normalized = normalized.substring(0, 500) + "…";
        result.add(ChatMessage.user("이전 semantic model이 다음 domain validation을 통과하지 못했다: "
                + normalized + " 위 규칙과 SOURCE_SNAPSHOT_JSON을 다시 대조해 전체 semantic model JSON을 교정하라."));
        return List.copyOf(result);
    }
}
