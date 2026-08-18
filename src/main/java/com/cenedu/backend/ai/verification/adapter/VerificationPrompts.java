package com.cenedu.backend.ai.verification.adapter;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumContext;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotLearningGuide;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotRubricItem;

/**
 * 검증 호출의 프롬프트. 세 종류이며 <b>서로 다른 호출</b>이다.
 *
 * <p>Solver 는 Blind 를 보고, 루브릭·altText 검사는 원본을 본다. 한 호출에 합치지 않는다 —
 * 합치면 같은 컨텍스트에 정답이 들어가고, 그 순간 Solver 의 독립성이 사라진다.
 *
 * <p>{@code LlmClient} 가 평문만 돌려주므로 JSON 을 프롬프트로 요구하고 Adapter 가 파싱한다.
 * 구조화 출력 경로가 아니라서 형식 위반이 일어날 수 있고, 그건 ERROR 로 처리한다.
 */
final class VerificationPrompts {

    private VerificationPrompts() {
    }

    static String solverSystemPrompt() {
        return """
                당신은 중학교 수학 문항을 푸는 채점자다. 주어진 문항을 혼자 풀어라.

                규칙:
                - 문항에 정답이 주어지지 않는다. 스스로 풀어야 한다.
                - answerUnits 의 각 unitKey 마다 답을 하나씩 낸다.
                - questionType 이 MULTIPLE_CHOICE 면 답은 보기의 choiceKey 다(예: C2). 보기 내용을 적지 않는다.
                - questionType 이 ESSAY 면 solved 를 false 로 두고 reason 에 서술형임을 적는다.
                - compareMethod 는 답의 형태만 알려 준다. SET 이면 원소를 쉼표로, VALUE·EXACT 면 하나의 값으로 쓴다.
                - 수식은 LaTeX 로 쓴다. displayUnit 이 있으면 단위는 답에 쓰지 않는다.
                - 문항이 모순되거나 정보가 부족해 풀 수 없으면 solved 를 false 로 둔다. 추측하지 않는다.
                - reason 은 한 줄이다. 풀이 과정을 적지 않는다.
                - JSON을 쓰기 전에는 내부적으로 계산을 단계대로 수행하고 최종 답을 한 번 더 역검산한다.
                - 지수나 분수를 임의로 생략하지 마라. 소인수분해로 최대공약수를 구할 때는
                  공통 소인수의 지수 중 작은 값을 각각 적용한 뒤 그 값들을 모두 곱한다.

                오직 아래 JSON 만 출력한다. 설명이나 코드 펜스를 덧붙이지 않는다.
                {"solved": true, "answers": [{"unitKey": "MAIN", "answer": "..."}], "reason": "한 줄 근거"}
                """;
    }

    static String solverUserPrompt(String blindQuestionJson) {
        return "다음 문항을 풀어라.\n\n" + blindQuestionJson;
    }

    /**
     * 원본 검사. 정답이 든 스냅샷을 보고 결함을 찾는다.
     *
     * <p>Solver 와 <b>다른 호출</b>이다. 같은 컨텍스트에 두면 Solver 가 정답을 보게 되고,
     * 그 순간 독립 풀이가 아니게 된다.
     *
     * <p>루브릭 심사를 이 호출에 묶는다. 입력이 같은 원본이라 나눌 이유가 없고, 문항당 호출 수가
     * 곧 학습지당 비용이다. 10문항이면 호출 하나 차이가 10회다.
     *
     * <p>구조 불변식은 프롬프트에 넣지 않는다. {@code SnapshotStructuralValidator} 가 코드로
     * 판정하는 것을 모델에게 또 물으면 같은 위반이 Finding 두 개로 나간다.
     *
     * @param includeRubric ESSAY 일 때만 루브릭 절을 요구한다
     */
    static String contentIntegritySystemPrompt(boolean includeRubric) {
        String rubricSection = includeRubric ? """

                [RUBRIC] 서술형 채점 기준의 의미를 본다. 항목 수·가중치 합 같은 형식은 이미 검사되었다.
                - OUT_OF_SCOPE: 문항 범위 밖을 요구한다. 어떤 답안도 채울 수 없어 만점이 불가능하다.
                - UNCOVERED: 문항이 요구하는 요소가 어느 기준에도 없다. 빠뜨려도 만점이 된다.
                - OVERLAPPING: 두 기준이 같은 것을 잰다. 실수 하나에 이중 감점이 된다.
                - UNJUDGEABLE: 충족 여부를 가릴 수 없다. 채점자마다 결과가 달라진다.
                kind 에 위 네 축 중 하나를 적는다.
                """ : "";

        return """
                당신은 이미 만들어진 중학교 수학 문항을 심사한다. 문항에는 정답과 해설이 함께 주어진다.
                결함을 찾는 것이 일이며, 문항을 다시 풀거나 고쳐 쓰지 않는다.

                [EXPLANATION] 해설을 본다.
                - 해설의 결론이 정답과 모순되는가.
                - 해설이 스냅샷에 없는 값·조건을 끌어다 쓰는가.

                [LEAKAGE] 개념 안내(learningGuide)를 본다. 학생이 문제를 푸는 동안 보는 화면이다.
                - kind=ANSWER_VALUE: 정답 값이나 최종 계산 결과를 그대로 담았다.
                - kind=SOLUTION_DIRECTION: 값은 없지만 어떤 순서로 풀라고 방향을 지정했다.
                learningGuide 가 없으면 이 항목은 건너뛴다.

                [CURRICULUM] 요청한 교육과정과 발문이 실제로 다루는 수학 개념을 비교한다.
                - 메타데이터 ID가 같아도 문제의 핵심 풀이가 소단원 개념과 명백히 다르면 결함이다.
                - 소단원 범위의 응용이거나 소재만 다른 것은 결함이 아니다.
                - 명백한 이탈일 때만 type=CURRICULUM으로 반환한다.
                %s
                규칙:
                1. 결함 유형을 STRUCTURE / LEAKAGE / EXPLANATION / CURRICULUM / RUBRIC 중 하나로 분류한다.
                2. location 에는 문제가 있는 위치(필드 경로·인덱스)만 적는다.
                   정답 값·계산 결과를 그대로 옮겨 적지 않는다. detail 에도 적지 않는다.
                   예: learningGuide.keyPoints[2] (O) / 정답 420 이 노출됨 (X)
                3. 결함이 없으면 빈 배열을 반환한다. 없는 결함을 만들어내지 않는다.
                   찾을 것이 없는 문항이 정상이다.

                오직 아래 JSON 만 출력한다. 설명이나 코드 펜스를 덧붙이지 않는다.
                {"findings": [{"type": "LEAKAGE", "kind": "ANSWER_VALUE", \
                "location": "learningGuide.keyPoints[2]", "detail": "한 줄 설명"}]}
                """.formatted(rubricSection);
    }

    /** 원본 검사에 넣는 입력. 정답·해설·개념 안내·채점 기준을 모두 담는다. */
    static String contentIntegrityUserPrompt(
            QuestionSnapshotV1 snapshot,
            CurriculumContext expectedCurriculum
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("[유형] ").append(snapshot.metadata() == null
                ? "알 수 없음" : snapshot.metadata().questionType()).append('\n');

        builder.append("\n[요청 교육과정]\n");
        if (expectedCurriculum == null) {
            builder.append("(없음)\n");
        } else {
            builder.append("학년: ").append(expectedCurriculum.grade()).append('\n');
            builder.append("학기: ").append(expectedCurriculum.semester()).append('\n');
            builder.append("대단원: ").append(expectedCurriculum.majorUnitName()).append('\n');
            builder.append("중단원: ").append(expectedCurriculum.middleUnitName()).append('\n');
            builder.append("소단원: ").append(expectedCurriculum.subUnitName()).append('\n');
        }

        builder.append("\n[발문]\n");
        snapshot.contentBlocks().forEach(block -> {
            if (block == null) {
                return;
            }
            if (block.text() != null) {
                builder.append(block.blockKey()).append(": ").append(block.text()).append('\n');
            }
            if (block.markup() != null) {
                builder.append(block.blockKey()).append(" (표): ").append(block.markup()).append('\n');
            }
            if (block.assetRef() != null) {
                builder.append(block.blockKey()).append(" (그림 ")
                        .append(block.assetRef()).append(")\n");
            }
        });

        if (!snapshot.choices().isEmpty()) {
            builder.append("\n[보기]\n");
            snapshot.choices().forEach(choice -> builder.append(choice.choiceKey())
                    .append(": ").append(choice.content()).append('\n'));
        }
        if (!snapshot.steps().isEmpty()) {
            builder.append("\n[풀이 단계]\n");
            snapshot.steps().forEach(step -> builder.append(step.stepKey())
                    .append(": ").append(step.label()).append('\n'));
        }

        builder.append("\n[정답]\n");
        snapshot.answerUnits().forEach(unit -> {
            if (unit == null) {
                return;
            }
            builder.append(unit.unitKey()).append(" (").append(unit.compareMethod()).append("): ")
                    .append(unit.answerRaw() == null ? "(없음)" : unit.answerRaw()).append('\n');
        });

        builder.append("\n[해설]\n").append(snapshot.explanation()).append('\n');

        if (snapshot.learningGuide() != null) {
            SnapshotLearningGuide guide = snapshot.learningGuide();
            builder.append("\n[개념 안내]\n");
            builder.append("제목: ").append(guide.conceptTitle()).append('\n');
            builder.append("요약: ").append(guide.summary()).append('\n');
            if (guide.keyPoints() != null) {
                for (int index = 0; index < guide.keyPoints().size(); index++) {
                    builder.append("keyPoints[").append(index).append("]: ")
                            .append(guide.keyPoints().get(index)).append('\n');
                }
            }
        } else {
            builder.append("\n[개념 안내]\n(없음)\n");
        }

        if (!snapshot.rubricItems().isEmpty()) {
            builder.append("\n[채점 기준]\n");
            snapshot.rubricItems().forEach(rubric -> builder.append(rubric.rubricKey())
                    .append(" (").append(rubric.weightPercent()).append("%): ")
                    .append(rubric.criterion()).append('\n'));
        }
        return builder.toString();
    }

    static String rubricSystemPrompt() {
        return """
                당신은 서술형 채점 기준을 심사한다. 기준의 개수·가중치 합 같은 형식은 이미 검사되었다.
                여기서는 의미만 본다.

                네 축으로 본다.
                - OUT_OF_SCOPE: 문항 범위 밖을 요구한다. 어떤 답안도 채울 수 없어 만점이 불가능하다.
                - UNCOVERED: 문항이 요구하는 요소가 어느 기준에도 없다. 빠뜨려도 만점이 된다.
                - OVERLAPPING: 두 기준이 같은 것을 잰다. 실수 하나에 이중 감점이 된다.
                - UNJUDGEABLE: 충족 여부를 가릴 수 없다. 채점자마다 결과가 달라진다.

                문제가 없으면 axis 를 빈 문자열로 둔다.
                문제가 있으면 axis 에 위 네 축 중 하나를 대문자로 적고, detail 에 어느 기준인지 한 줄로 적는다.

                오직 아래 JSON 만 출력한다.
                {"axis": "OVERLAPPING", "detail": "R2와 R3이 모두 소인수분해 수행을 요구합니다."}
                """;
    }

    static String rubricUserPrompt(QuestionSnapshotV1 snapshot) {
        StringBuilder builder = new StringBuilder("[문항]\n");
        snapshot.contentBlocks().forEach(block -> {
            if (block != null && block.text() != null) {
                builder.append(block.text()).append('\n');
            }
        });
        builder.append("\n[채점 기준]\n");
        for (SnapshotRubricItem rubric : snapshot.rubricItems()) {
            if (rubric != null) {
                builder.append(rubric.rubricKey()).append(" (")
                        .append(rubric.weightPercent()).append("%): ")
                        .append(rubric.criterion()).append('\n');
            }
        }
        return builder.toString();
    }

    static String assetSystemPrompt() {
        return """
                당신은 문항의 그림 설명(altText)을 심사한다.

                두 가지를 본다.
                - LEAK: altText 에 그림에 보이지 않는 것이 들어 있다. 정답, 계산 결과, 풀이 추론이 그렇다.
                  altText 는 그림에 실제로 보이는 것만 설명해야 한다.
                - MISMATCH: altText 가 발문과 어긋난다. 발문이 전제하는 그림과 다른 그림을 설명한다.

                문제가 없으면 issue 를 빈 문자열로 둔다.
                문제가 있으면 issue 에 LEAK 또는 MISMATCH 를 적고 detail 에 어느 assetKey 인지 한 줄로 적는다.

                오직 아래 JSON 만 출력한다.
                {"issue": "LEAK", "detail": "F1 의 altText 가 넓이 계산 결과를 담고 있습니다."}
                """;
    }

    /**
     * altText 검사는 <b>Blind 가 아닌 원본</b>으로 한다. Blind 에는 정답이 없어서
     * "altText 에 정답이 새어 있는지"를 판정할 수 없다.
     */
    static String assetUserPrompt(QuestionSnapshotV1 snapshot) {
        StringBuilder builder = new StringBuilder("[발문]\n");
        snapshot.contentBlocks().forEach(block -> {
            if (block == null) {
                return;
            }
            if (block.text() != null) {
                builder.append(block.text()).append('\n');
            }
            if (block.assetRef() != null) {
                builder.append("(여기에 그림 ").append(block.assetRef()).append(" 이 들어간다)\n");
            }
        });
        builder.append("\n[그림 설명]\n");
        snapshot.assets().forEach(asset -> {
            if (asset != null) {
                builder.append(asset.assetKey()).append(": ").append(asset.altText()).append('\n');
            }
        });
        builder.append("\n[정답]\n");
        List<String> answered = snapshot.answerUnits().stream()
                .filter(unit -> unit != null && unit.answerRaw() != null)
                .map(unit -> unit.unitKey() + ": " + unit.answerRaw())
                .toList();
        if (answered.isEmpty()) {
            // 서술형처럼 종점 값이 없는 문항이다. altText 에 정답이 새었는지는 판정할 수 없고,
            // 발문과의 정합만 남는다. 그 사실을 프롬프트에 명시해 모델이 없는 정답을 상상하지 않게 한다.
            builder.append("(대조 가능한 정답 값이 없다)\n");
        } else {
            answered.forEach(line -> builder.append(line).append('\n'));
        }
        return builder.toString();
    }
}
