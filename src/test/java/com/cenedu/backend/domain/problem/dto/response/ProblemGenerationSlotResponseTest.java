package com.cenedu.backend.domain.problem.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationItemStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;
import com.cenedu.backend.global.common.enums.CustomStage;
import org.junit.jupiter.api.Test;

class ProblemGenerationSlotResponseTest {

    private final QuestionSnapshotV1 snapshot = new QuestionSnapshotV1(
            1, null, List.of(), List.of(), List.of(), List.of(), List.of(),
            null, null, List.of());

    @Test
    void 자산이_아직_준비되지_않으면_generating_asset으로_표시한다() {
        AuthoringSlotDisplayStatus status = AuthoringSlotDisplayStatus.resolve(
                GenerationItemStatus.GENERATING,
                AuthoringOperationStatus.GENERATING,
                true,
                false);

        assertThat(status).isEqualTo(AuthoringSlotDisplayStatus.GENERATING_ASSET);
    }

    @Test
    void 완료된_문항은_ready로_표시한다() {
        AuthoringSlotDisplayStatus status = AuthoringSlotDisplayStatus.resolve(
                GenerationItemStatus.SUCCEEDED,
                AuthoringOperationStatus.IDLE,
                false,
                false);

        AuthoringProblemSnapshotResponse preview = new AuthoringProblemSnapshotResponse(
                10L, 20L, null, snapshot);
        ProblemGenerationSlotResponse response = new ProblemGenerationSlotResponse(
                1, 100L, 10L, status, preview, null, false);

        assertThat(response.status()).isEqualTo(AuthoringSlotDisplayStatus.READY);
        assertThat(response.preview()).isSameAs(preview);
    }

    @Test
    void ready_문항에_미리보기가_없으면_거절한다() {
        assertThatThrownBy(() -> new ProblemGenerationSlotResponse(
                1, 100L, 10L, AuthoringSlotDisplayStatus.READY, null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void job_응답은_슬롯수와_totalCount를_일치시킨다() {
        ProblemGenerationSlotResponse slot = new ProblemGenerationSlotResponse(
                1, 100L, 10L, AuthoringSlotDisplayStatus.QUEUED, null, null, false);

        ProblemGenerationJobStatusResponse response = new ProblemGenerationJobStatusResponse(
                1000L, GenerationJobStatus.RUNNING, 1, 0, List.of(slot));

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.slots()).containsExactly(slot);
    }

    @Test
    void 맞춤_단계와_출처를_응답에_보존한다() {
        ProblemGenerationSlotResponse response = new ProblemGenerationSlotResponse(
                1, 100L, 10L, CustomProblemStageFormatter.format(CustomStage.SIMILAR),
                301L, null, AuthoringSlotDisplayStatus.QUEUED, null, null, false);

        assertThat(response.customStage()).isEqualTo("similar");
        assertThat(response.sourceQuestionId()).isEqualTo(301L);
        assertThat(response.originQuestionId()).isNull();
        assertThat(CustomProblemStageFormatter.format(CustomStage.ADVANCED)).isEqualTo("advanced");
        assertThat(CustomProblemStageFormatter.format(null)).isNull();
    }
}
