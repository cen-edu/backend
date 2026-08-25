package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;

class ProblemGenerationPromptFactoryLatexTest {

    @Test
    void LaTeX를_JSON_원문에서_이중_백슬래시로_출력하도록_지시한다() {
        ProblemGenerationCommand command = command();

        String systemPrompt = new ProblemGenerationPromptFactory().create(command).systemPrompt();

        assertThat(systemPrompt)
                .contains("{\"text\":\"$\\\\frac{3}{4}$\"}")
                .contains("{\"text\":\"$2\\\\times3$\"}");
    }

    @Test
    void 그래프_문항은_시각_정보를_본문에_중복하지_않도록_지시한다() {
        String systemPrompt = new ProblemGenerationPromptFactory().create(command()).systemPrompt();

        assertThat(systemPrompt)
                .contains("visualDescription에만 적고 text에 장황하게 중복하지 마라")
                .contains("true이면 text와")
                .contains("visualDescription을 함께 보아")
                .contains("풀이에 필요한 모든 정보를 확인한다")
                .contains("그래프에 표시할 정보를 text에 다시 설명하지 마라");
    }

    @Test
    void 검증실패_재시도에는_직전후보와_교정규칙을_함께_제공한다() {
        ProblemGenerationOutput previous = new ProblemGenerationOutput(
                "이전 문제", List.of(), List.of(), List.of(), List.of(), "이전 해설",
                new ProblemGenerationOutput.LearningGuideOutput(
                        "개념", "요약", List.of("핵심")),
                List.of(), List.of(), false, null, null);

        ProblemGenerationPrompt prompt = new ProblemGenerationPromptFactory().create(
                command(), previous, List.of("choices: 정답 보기가 없습니다."));
        String messages = prompt.messages().stream().map(message -> message.content())
                .collect(java.util.stream.Collectors.joining("\n"));

        assertThat(messages)
                .contains("PREVIOUS_CANDIDATE_JSON")
                .contains("이전 문제")
                .contains("PREVIOUS_ATTEMPT_VIOLATIONS")
                .contains("정상 필드는 유지")
                .contains("부분 패치가 아니라 CANDIDATE 스키마 전체");
    }

    @Test
    void 시각의존성_위반에는_그래프생성_또는_텍스트자립_교정을_지시한다() {
        ProblemGenerationOutput previous = new ProblemGenerationOutput(
                "다음 그래프를 보고 답하시오.", List.of(), List.of(), List.of(), List.of(), "해설",
                new ProblemGenerationOutput.LearningGuideOutput(
                        "개념", "요약", List.of("핵심")),
                List.of(), List.of(), false, null, null);

        ProblemGenerationPrompt prompt = new ProblemGenerationPromptFactory().create(
                command(), previous,
                List.of("visualDependency: 실제 그림·그래프·표 없이 시각 자료를 참조할 수 없습니다."));
        String messages = prompt.messages().stream().map(message -> message.content())
                .collect(java.util.stream.Collectors.joining("\n"));

        assertThat(messages)
                .contains("VISUAL_CONSISTENCY_REPAIR")
                .contains("visualRequired=true")
                .contains("visualKind=\"COORDINATE_GRAPH\"")
                .contains("visualRequired=false");
    }

    private ProblemGenerationCommand command() {
        return new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.COMPREHENSIVE_ASSESSMENT_SHORTAGE,
                new GenerationSpecification(QuestionType.SHORT_INPUT, "low", null,
                        List.of(), false, VisualGenerationRequirement.none()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1,
                        null, 19L, "변화와 관계", "문자와 식", "일차방정식"),
                List.of(), List.of());
    }
}
