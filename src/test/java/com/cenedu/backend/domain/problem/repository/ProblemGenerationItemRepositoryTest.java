package com.cenedu.backend.domain.problem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemGenerationItem;
import com.cenedu.backend.domain.problem.entity.ProblemGenerationJob;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.domain.problem.support.ProblemQuestionFixtures;
import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.domain.member.repository.MemberAccountRepository;
import com.cenedu.backend.global.common.enums.CustomStage;
import com.cenedu.backend.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
@Transactional
class ProblemGenerationItemRepositoryTest {

    @Autowired ProblemGenerationItemRepository itemRepository;
    @Autowired ProblemGenerationJobRepository jobRepository;
    @Autowired ProblemAuthoringSessionRepository sessionRepository;
    @Autowired ProblemQuestionRepository questionRepository;
    @Autowired MemberAccountRepository memberAccountRepository;

    @Test
    void savesAndReadsCustomStageAndOriginQuestionId() {
        Long teacherId = memberAccountRepository.save(MemberAccount.createTeacher(
                "task5-teacher", "hash", "Task5 Teacher")).getId();
        ProblemQuestion origin = ProblemQuestionFixtures.imported();
        ReflectionTestUtils.setField(origin, "topicCode", null);
        origin = questionRepository.save(origin);
        ProblemGenerationJob job = jobRepository.save(ProblemGenerationJob.create(
                teacherId, UUID.randomUUID(), GenerationJobType.PERSONALIZED));
        ProblemAuthoringSession session = sessionRepository.save(
                ProblemAuthoringSession.createGenerating(teacherId));
        ProblemGenerationItem item = ProblemGenerationItem.create(
                job.getId(), 1, UUID.randomUUID(), session.getId(),
                com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose
                        .PERSONALIZED_APPLICATION,
                1, "{}", CustomStage.ADVANCED, origin.getId());

        ProblemGenerationItem saved = itemRepository.saveAndFlush(item);
        ProblemGenerationItem reloaded = itemRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getCustomStage()).isEqualTo(CustomStage.ADVANCED);
        assertThat(reloaded.getOriginQuestionId()).isEqualTo(origin.getId());
    }
}
