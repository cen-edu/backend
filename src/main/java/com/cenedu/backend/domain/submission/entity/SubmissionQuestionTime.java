package com.cenedu.backend.domain.submission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문항별 누적 풀이 시간.
 *
 * <p>클라이언트 측정값이라 참고 지표이고 채점·점수에는 관여하지 않는다.
 * 미제출 학생은 행이 없어 답안 행과 비대칭이다.
 */
@Entity
@Getter
@Table(name = "submission_question_time",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_submission_question_time",
                columnNames = {"assignment_student_id", "worksheet_item_id"}),
        indexes = @Index(name = "idx_submission_question_time_worksheet_item",
                columnList = "worksheet_item_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionQuestionTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** worksheet 도메인의 학생별 배정 ID. */
    @Column(name = "assignment_student_id", nullable = false)
    private Long assignmentStudentId;

    /** worksheet 도메인의 학습지 문항 ID. */
    @Column(name = "worksheet_item_id", nullable = false)
    private Long worksheetItemId;

    @Column(name = "time_spent_seconds", nullable = false)
    private int timeSpentSeconds;

    private SubmissionQuestionTime(Long assignmentStudentId, Long worksheetItemId,
                                   int timeSpentSeconds) {
        this.assignmentStudentId = assignmentStudentId;
        this.worksheetItemId = worksheetItemId;
        this.timeSpentSeconds = timeSpentSeconds;
    }

    /** 문항 하나에 머문 누적 시간을 기록한다. */
    public static SubmissionQuestionTime create(Long assignmentStudentId, Long worksheetItemId,
                                                int timeSpentSeconds) {
        return new SubmissionQuestionTime(assignmentStudentId, worksheetItemId, timeSpentSeconds);
    }
}
