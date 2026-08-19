package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.validation.*;
import com.cenedu.backend.domain.problem.authoring.verification.*;
import com.cenedu.backend.domain.problem.support.*;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 외부 API 없이 생성→S1→검증→최종 Entity 계약이 수직으로 호환되는지 확인한다. */
class ProblemAuthoringEndToEndTest {
    @Test
    void 문제은행_3개와_AI_2개가_동일한_S1_최종화_계약으로_합쳐진다() {
        List<String> slots = List.of("BANK", "BANK", "BANK", "AI", "AI");
        FakeProblemGenerationPort generation = new FakeProblemGenerationPort();
        FakeProblemVerificationPort verification = new FakeProblemVerificationPort(true);
        SnapshotStructuralValidator structural = new SnapshotStructuralValidator();
        SnapshotNormalizedValidator normalized = new SnapshotNormalizedValidator(structural);
        ProblemSnapshotEntityMapper persistenceMapper = new ProblemSnapshotEntityMapper(new ObjectMapper());

        List<com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft> aiCandidates =
                slots.stream().filter("AI"::equals).map(ignored -> generation.generate(command())).toList();
        for (var candidate : aiCandidates) {
            structural.validate(candidate.snapshot());
            normalized.validate(candidate.snapshot());
            var request = new ProblemVerificationRequest(UUID.randomUUID(), VerificationScope.CONTENT,
                    VerificationOperationType.CREATE, candidate, DraftAssetManifest.planned(List.of()),
                    new VerificationExpectation(QuestionType.SHORT_INPUT, "mid",
                            command().curriculum(), null, List.of(), List.of()),
                    new GenerationVerificationContext(GenerationPurpose.GENERAL_LEARNING_SHORTAGE, List.of()));
            assertThat(verification.verify(request).overallStatus()).isEqualTo(VerificationOverallStatus.PASSED);
            ProblemQuestionPersistenceBundle bundle = persistenceMapper.map(candidate.snapshot(), Map.of());
            assertThat(bundle.question().getQuestionType()).isEqualTo(QuestionType.SHORT_INPUT);
            assertThat(bundle.answerUnits()).hasSize(1);
        }

        assertThat(slots).hasSize(5);
        assertThat(generation.callCount()).isEqualTo(2);
        assertThat(verification.callCount()).isEqualTo(2);
    }

    @Test
    void 한_AI_문항의_실패가_이미_완료된_다른_slot_결과를_지우지_않는다() {
        FakeProblemGenerationPort generation = new FakeProblemGenerationPort()
                .failNext(new IllegalStateException("provider"));
        java.util.List<String> slotStates = new java.util.ArrayList<>(List.of("READY", "GENERATING"));

        assertThatThrownBy(() -> generation.generate(command())).isInstanceOf(IllegalStateException.class);
        slotStates.set(1, "FAILED");

        assertThat(slotStates).containsExactly("READY", "FAILED");
        assertThat(generation.callCount()).isEqualTo(1);
    }

    private ProblemGenerationCommand command() {
        return new ProblemGenerationCommand(UUID.randomUUID(), null, GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(QuestionType.SHORT_INPUT, "mid", null, List.of()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                        "수와 연산", "사칙연산", "덧셈"),
                List.of(), List.of());
    }
}
