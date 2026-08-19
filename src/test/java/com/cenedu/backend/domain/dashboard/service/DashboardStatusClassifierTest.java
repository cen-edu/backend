package com.cenedu.backend.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.cenedu.backend.domain.dashboard.entity.enums.AssignmentProgressStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardAssignmentStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardResultStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardStudentStatus;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DashboardStatusClassifierTest {

    private final DashboardStatusClassifier classifier = new DashboardStatusClassifier();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-08-14T12:00:00+09:00");

    @Test
    @DisplayName("학생 상태는 지연을 최우선으로 하고 이후 60퍼센트 기준을 적용한다")
    void classifiesStudentPriority() {
        assertThat(classifier.classifyStudent(true, 3, new BigDecimal("90.0")))
                .isEqualTo(DashboardStudentStatus.DELAYED);
        assertThat(classifier.classifyStudent(false, 0, null))
                .isEqualTo(DashboardStudentStatus.INSUFFICIENT_DATA);
        assertThat(classifier.classifyStudent(false, 2, new BigDecimal("59.9")))
                .isEqualTo(DashboardStudentStatus.NEEDS_SUPPORT);
        assertThat(classifier.classifyStudent(false, 2, new BigDecimal("60.0")))
                .isEqualTo(DashboardStudentStatus.GOOD);
    }

    @Test
    @DisplayName("학생별 배정 상태를 여섯 화면 상태로 변환한다")
    void classifiesAssignmentProgress() {
        assertThat(classifier.classifyProgress(null, 0, now.plusDays(1), now))
                .isEqualTo(AssignmentProgressStatus.NOT_ASSIGNED);
        assertThat(classifier.classifyProgress(
                AssignmentStatus.NOT_STARTED, 0, now.plusDays(1), now))
                .isEqualTo(AssignmentProgressStatus.NOT_STARTED);
        assertThat(classifier.classifyProgress(
                AssignmentStatus.NOT_STARTED, 1, now.plusDays(1), now))
                .isEqualTo(AssignmentProgressStatus.IN_PROGRESS);
        assertThat(classifier.classifyProgress(
                AssignmentStatus.SUBMITTED, 1, now.minusDays(1), now))
                .isEqualTo(AssignmentProgressStatus.GRADING_PENDING);
        assertThat(classifier.classifyProgress(
                AssignmentStatus.GRADED, 1, now.minusDays(1), now))
                .isEqualTo(AssignmentProgressStatus.COMPLETED);
        assertThat(classifier.classifyProgress(
                AssignmentStatus.NOT_STARTED, 0, now.minusDays(1), now))
                .isEqualTo(AssignmentProgressStatus.OVERDUE);
    }

    @Test
    @DisplayName("학급 배정은 전원 채점 완료를 우선하고 이후 기한을 확인한다")
    void classifiesClassAssignment() {
        assertThat(classifier.classifyAssignment(2, 2, now.minusDays(1), now))
                .isEqualTo(DashboardAssignmentStatus.COMPLETED);
        assertThat(classifier.classifyAssignment(2, 1, now.minusDays(1), now))
                .isEqualTo(DashboardAssignmentStatus.OVERDUE);
        assertThat(classifier.classifyAssignment(2, 1, now.plusDays(1), now))
                .isEqualTo(DashboardAssignmentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("채점 단계는 제출자 기준으로 판정하고 확정을 가장 먼저 본다")
    void classifiesResultStage() {
        // 제출 2 · 채점 1 → 아직 채점 중
        assertThat(classifier.classifyResult(2, 1, 0))
                .isEqualTo(DashboardResultStatus.GRADING);
        // 제출자 전원 채점 완료, 확정 전
        assertThat(classifier.classifyResult(2, 2, 0))
                .isEqualTo(DashboardResultStatus.GRADED);
        // 확정 후 정정으로 채점이 덜 된 상태가 되어도 공개는 되돌아가지 않는다
        assertThat(classifier.classifyResult(2, 1, 2))
                .isEqualTo(DashboardResultStatus.RELEASED);
    }

    @Test
    @DisplayName("미제출자만 있는 배정은 채점 단계를 말하지 않는다")
    void hasNoResultStageWithoutSubmission() {
        assertThat(classifier.classifyResult(0, 0, 0)).isNull();
    }

    @Test
    @DisplayName("전원 채점 완료라도 확정 전이면 완료가 아니라 채점 완료다")
    void separatesProgressAxisFromResultAxis() {
        assertThat(classifier.classifyAssignment(2, 2, now.plusDays(1), now))
                .isEqualTo(DashboardAssignmentStatus.COMPLETED);
        assertThat(classifier.classifyResult(2, 2, 0))
                .isEqualTo(DashboardResultStatus.GRADED);
    }
}
