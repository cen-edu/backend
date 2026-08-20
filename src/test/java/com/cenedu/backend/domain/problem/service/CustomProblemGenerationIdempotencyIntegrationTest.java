package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.domain.member.repository.MemberAccountRepository;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.domain.problem.repository.ProblemGenerationItemRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.domain.problem.support.ProblemQuestionFixtures;
import com.cenedu.backend.global.common.enums.CustomStage;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
@Transactional
class CustomProblemGenerationIdempotencyIntegrationTest {
    @Autowired ProblemGenerationJobService jobService;
    @Autowired ProblemGenerationItemRepository itemRepository;
    @Autowired MemberAccountRepository memberRepository;
    @Autowired ProblemQuestionRepository questionRepository;

    @Test
    void sameTeacherAndClientRequestIdReturnsSameJobWithoutNewItems() {
        Long teacherId = teacher("task11-teacher");
        Long originId = originQuestion();
        UUID clientRequestId = UUID.randomUUID();
        ProblemGenerationPlan plan = plan(clientRequestId, originId);

        var first = jobService.create(teacherId, plan);
        var second = jobService.create(teacherId, plan);

        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(second.items()).hasSize(1);
        assertThat(itemRepository.findAllByJobIdOrderByItemOrder(first.jobId())).hasSize(1);
    }

    @Test
    void sameClientRequestIdForAnotherTeacherCreatesSeparateJob() {
        Long firstTeacher = teacher("task11-teacher-a");
        Long secondTeacher = teacher("task11-teacher-b");
        Long originId = originQuestion();
        UUID clientRequestId = UUID.randomUUID();

        var first = jobService.create(firstTeacher, plan(clientRequestId, originId));
        var second = jobService.create(secondTeacher, plan(clientRequestId, originId));

        assertThat(second.jobId()).isNotEqualTo(first.jobId());
    }

    private Long teacher(String loginId) {
        return memberRepository.save(MemberAccount.createTeacher(loginId, "hash", loginId)).getId();
    }

    private Long originQuestion() {
        ProblemQuestion question = ProblemQuestionFixtures.imported();
        ReflectionTestUtils.setField(question, "topicCode", null);
        return questionRepository.save(question).getId();
    }

    private ProblemGenerationPlan plan(UUID requestId, Long originId) {
        QuestionSnapshotV1 snapshot = new QuestionSnapshotV1(1,
                new com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata(
                        QuestionType.STEP_FILL, QuestionPresentation.TEXT_ONLY, "mid", 20L,
                        null, null, null), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, List.of());
        CurriculumScope curriculum = new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1,
                null, 20L, "대단원", "중단원", "소단원");
        ProblemGenerationCommand command = new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.PERSONALIZED_SIMILAR_SHORTAGE,
                new GenerationSpecification(QuestionType.STEP_FILL, "mid", null, List.of()),
                curriculum, List.of(new GenerationReference(GenerationReferenceRole.ORIGIN, originId, snapshot)), List.of());
        return new ProblemGenerationPlan(requestId, GenerationJobType.PERSONALIZED,
                List.of(new ProblemGenerationSlotPlan(1, GenerationSlotSource.AI_GENERATION,
                        null, originId, CustomStage.SIMILAR, null, java.util.Map.of(), command)));
    }
}
