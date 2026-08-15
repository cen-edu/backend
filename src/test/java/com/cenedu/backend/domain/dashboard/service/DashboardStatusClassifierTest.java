package com.cenedu.backend.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.cenedu.backend.domain.dashboard.entity.enums.AssignmentProgressStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardAssignmentStatus;
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
}
