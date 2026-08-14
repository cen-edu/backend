package com.cenedu.backend.domain.dashboard.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.cenedu.backend.domain.dashboard.entity.enums.AssignmentProgressStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardAssignmentStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardStudentStatus;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import org.springframework.stereotype.Component;

/** DB 진행값과 기한·정답률을 대시보드 표시 상태로 변환한다. */
@Component
public class DashboardStatusClassifier {

    private static final BigDecimal SUPPORT_THRESHOLD = BigDecimal.valueOf(60);

    /** 지연을 우선하고 이후 집계 가능 여부와 60% 기준으로 학생 상태를 분류한다. */
    public DashboardStudentStatus classifyStudent(
            boolean delayed,
            int gradedItemCount,
            BigDecimal accuracyRate
    ) {
        if (delayed) {
            return DashboardStudentStatus.DELAYED;
        }
        if (gradedItemCount == 0 || accuracyRate == null) {
            return DashboardStudentStatus.INSUFFICIENT_DATA;
        }
        if (accuracyRate.compareTo(SUPPORT_THRESHOLD) < 0) {
            return DashboardStudentStatus.NEEDS_SUPPORT;
        }
        return DashboardStudentStatus.GOOD;
    }

    /** 학생별 배정 정본과 진행 수·기한을 화면의 여섯 진행 상태로 변환한다. */
    public AssignmentProgressStatus classifyProgress(
            AssignmentStatus status,
            int progressCount,
            OffsetDateTime dueAt,
            OffsetDateTime now
    ) {
        if (status == null) {
            return AssignmentProgressStatus.NOT_ASSIGNED;
        }
        if (status == AssignmentStatus.GRADED) {
            return AssignmentProgressStatus.COMPLETED;
        }
        if (status == AssignmentStatus.SUBMITTED) {
            return AssignmentProgressStatus.GRADING_PENDING;
        }
        if (status == AssignmentStatus.NOT_SUBMITTED
                || dueAt != null && dueAt.isBefore(now)) {
            return AssignmentProgressStatus.OVERDUE;
        }
        if (progressCount > 0) {
            return AssignmentProgressStatus.IN_PROGRESS;
        }
        return AssignmentProgressStatus.NOT_STARTED;
    }

    /** 학생별 제출·채점 인원과 기한을 학습지의 진행·완료·기한초과 상태로 변환한다. */
    public DashboardAssignmentStatus classifyAssignment(
            int studentCount,
            int gradedStudentCount,
            OffsetDateTime dueAt,
            OffsetDateTime now
    ) {
        if (studentCount > 0 && gradedStudentCount == studentCount) {
            return DashboardAssignmentStatus.COMPLETED;
        }
        if (dueAt.isBefore(now)) {
            return DashboardAssignmentStatus.OVERDUE;
        }
        return DashboardAssignmentStatus.IN_PROGRESS;
    }
}
