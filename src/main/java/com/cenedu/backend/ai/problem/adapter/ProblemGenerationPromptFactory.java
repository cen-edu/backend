package com.cenedu.backend.ai.problem.adapter;

import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import org.springframework.stereotype.Component;

/** 문제 유형별 생성 규칙을 공통 시스템 프롬프트와 결합한다. */
@Component
public class ProblemGenerationPromptFactory {

    /** 서버가 기대하는 S1 JSON 계약과 생성 조건을 프롬프트로 만든다. */
    public String create(ProblemGenerationCommand command) {
        var spec = command.specification();
        var curriculum = command.curriculumContext();
        String references = command.references() == null ? "없음" : command.references().stream()
                .map(GenerationReference::sourceQuestionId).map(String::valueOf).reduce((a, b) -> a + ", " + b)
                .orElse("없음");
        return """
                당신은 초중등 수학 문제 출제자다. 아래 조건으로 문제 하나를 생성하라.
                반드시 JSON 객체만 출력하고 Markdown 코드 펜스를 사용하지 마라.
                schemaVersion은 1, metadata.questionType은 %s, metadata.difficulty는 %s,
                metadata.subUnitId는 %d로 고정한다. 생성된 requestId를 만들거나 수정하지 마라.
                contentBlocks에는 displayOrder 0의 TEXT 발문을 포함한다.
                explanation, learningGuide(conceptTitle, summary, keyPoints 1~3개)는 필수다.
                keyPoints는 직접적인 정답이나 계산 절차를 노출하지 않는다.
                choices/answerUnits/steps/rubricItems/assets는 사용하지 않으면 빈 배열이다.
                참고 문제 ID: %s. 교육과정: %s > %s > %s.

                유형별 규칙:
                %s

                JSON 필드: schemaVersion, metadata, contentBlocks, assets, choices, steps,
                answerUnits, explanation, learningGuide, rubricItems.
                """.formatted(spec.questionType(), spec.difficulty(), curriculum.subUnitId(), references,
                curriculum.majorUnitName(), curriculum.middleUnitName(), curriculum.subUnitName(), typeRules(spec.questionType().name()));
    }

    private String typeRules(String type) {
        return switch (type) {
            case "MULTIPLE_CHOICE" -> "choices는 C1부터, answerUnits에는 MAIN과 CHOICE 비교 방식으로 정답 보기 키를 둔다.";
            case "SHORT_INPUT" -> "answerUnits에는 MAIN 하나를 두고 compareMethod는 VALUE, EXACT, SET, SUBST 중 하나다.";
            case "STEP_FILL" -> "steps는 ST1부터 만들고 각 빈칸은 B1부터, ANSWER_REF는 앞선 B만 참조한다.";
            case "ESSAY" -> "풀이 과정을 서술하는 발문과 rubricItems 2~5개, 가중치 합계 100을 제공한다.";
            default -> throw new IllegalArgumentException("지원하지 않는 문제 유형: " + type);
        };
    }
}
