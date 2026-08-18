package com.cenedu.backend.domain.worksheet.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
import com.cenedu.backend.domain.worksheet.repository.row.AssignmentStudentCountRow;
import com.cenedu.backend.domain.worksheet.repository.row.LearningStatusAssignmentRow;
import com.cenedu.backend.domain.worksheet.repository.row.LearningStatusSummaryRow;
import com.cenedu.backend.domain.worksheet.repository.row.WorksheetCountRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorksheetAssignmentRepository extends JpaRepository<WorksheetAssignment, Long> {

    /** 학습지의 배포 이력을 배포일 순서대로 조회한다. */
    List<WorksheetAssignment> findAllByWorksheetIdOrderByAssignedAtAsc(Long worksheetId);

    /** 학습지가 해당 반에 이미 배포됐는지 반환한다. */
    boolean existsByWorksheetIdAndClassId(Long worksheetId, Long classId);

    /** 학습지에 배포 행이 하나라도 있는지 반환한다. */
    boolean existsByWorksheetId(Long worksheetId);

    /**
     * 채점 목록용 배포 조회. 학습지를 함께 읽어 목록이 제목·유형·학년을 다시 조회하지 않게 한다.
     * 파생 상태(grading/graded/confirmed)는 컬럼이 아니라 서버 계산이라 여기서 거르지 않는다.
     */
    @Query("""
            select wa from WorksheetAssignment wa
            join fetch wa.worksheet w
            where w.ownerTeacherId = :teacherId
              and w.deletedAt is null
              and (:grade is null or w.grade = :grade)
              and (:semester is null or w.semester = :semester)
              and (:classId is null or wa.classId = :classId)
            order by wa.assignedAt desc, wa.id desc
            """)
    List<WorksheetAssignment> findAllForGrading(
            @Param("teacherId") long teacherId,
            @Param("grade") Short grade,
            @Param("semester") String semester,
            @Param("classId") Long classId);

    /** 채점 화면의 축인 배포 하나를 학습지까지 함께 읽는다. */
    @Query("""
            select wa from WorksheetAssignment wa
            join fetch wa.worksheet w
            where wa.id = :assignmentId and w.deletedAt is null
            """)
    Optional<WorksheetAssignment> findDetailById(@Param("assignmentId") long assignmentId);

    /** 지정한 학습지들의 배포 수를 학습지 ID별로 센다. */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.WorksheetCountRow(
                a.worksheet.id, count(a))
            from WorksheetAssignment a
            where a.worksheet.id in :worksheetIds
            group by a.worksheet.id
            """)
    List<WorksheetCountRow> countByWorksheetIdIn(@Param("worksheetIds") Collection<Long> worksheetIds);

    /**
     * 학습 현황 목록. 맞춤 배포도 같은 배열에 담는다 — 프론트가 {@code origin}으로 갈라 렌더한다.
     *
     * <p>키워드는 호출부가 소문자 {@code like} 패턴으로 만들어 넘긴다. 필터가 없으면 {@code "%"}다 —
     * {@code null}을 바인딩하면 타입을 추론하지 못해 {@code text ~~ bytea}로 깨진다.
     */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.LearningStatusAssignmentRow(
                wa.id, w.id, w.title, w.type, w.origin, wa.classId, w.grade, w.semester,
                wa.assignedAt, wa.dueAt, w.sourceAssignment.id)
            from WorksheetAssignment wa
            join wa.worksheet w
            where w.ownerTeacherId = :teacherId
              and w.deletedAt is null
              and (:grade is null or w.grade = :grade)
              and (:semester is null or w.semester = :semester)
              and (:classId is null or wa.classId = :classId)
              and lower(w.title) like :keyword
            order by wa.assignedAt desc, wa.id desc
            """)
    List<LearningStatusAssignmentRow> findAllForLearningStatus(
            @Param("teacherId") long teacherId,
            @Param("grade") Short grade,
            @Param("semester") String semester,
            @Param("classId") Long classId,
            @Param("keyword") String keyword);

    /**
     * 요약 카드의 배정-학생 집계. 목록과 같은 필터를 걸어 화면의 두 영역이 어긋나지 않게 한다.
     *
     * <p>집계 대상이 0행이면 {@code sum}이 {@code null}이라 {@code long} 언박싱에서 터진다.
     * {@code coalesce}로 0을 보장한다 — 배포는 있는데 배정된 학생이 없는 상태가 실제로 있다.
     *
     * <p>{@code inProgress}에 마감 조건이 붙는 이유는 마감이 지나면 진행 중이던 학생도 미제출이라,
     * 없으면 {@code unsubmitted}와 이중 계상되기 때문이다.
     */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.LearningStatusSummaryRow(
                coalesce(sum(case when was.status in (
                        com.cenedu.backend.global.common.enums.AssignmentStatus.SUBMITTED,
                        com.cenedu.backend.global.common.enums.AssignmentStatus.GRADED)
                    then 1L else 0L end), 0L),
                coalesce(sum(case when was.status = com.cenedu.backend.global.common.enums.AssignmentStatus.NOT_STARTED
                          and was.progressCount > 0 and wa.dueAt > :now
                    then 1L else 0L end), 0L),
                coalesce(sum(case when was.status = com.cenedu.backend.global.common.enums.AssignmentStatus.NOT_SUBMITTED
                          or (was.status = com.cenedu.backend.global.common.enums.AssignmentStatus.NOT_STARTED
                              and wa.dueAt <= :now)
                    then 1L else 0L end), 0L))
            from WorksheetAssignmentStudent was
            join was.assignment wa
            join wa.worksheet w
            where w.ownerTeacherId = :teacherId
              and w.deletedAt is null
              and (:grade is null or w.grade = :grade)
              and (:semester is null or w.semester = :semester)
              and (:classId is null or wa.classId = :classId)
              and lower(w.title) like :keyword
            """)
    LearningStatusSummaryRow summarizeLearningStatus(
            @Param("teacherId") long teacherId,
            @Param("grade") Short grade,
            @Param("semester") String semester,
            @Param("classId") Long classId,
            @Param("keyword") String keyword,
            @Param("now") OffsetDateTime now);

    /** 배포별 학생 수와 제출 수를 한 번에 센다. 배포 단위로 순회하지 않는다. */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.AssignmentStudentCountRow(
                was.assignment.id, count(was),
                coalesce(sum(case when was.status in (
                        com.cenedu.backend.global.common.enums.AssignmentStatus.SUBMITTED,
                        com.cenedu.backend.global.common.enums.AssignmentStatus.GRADED)
                    then 1L else 0L end), 0L))
            from WorksheetAssignmentStudent was
            where was.assignment.id in :assignmentIds
            group by was.assignment.id
            """)
    List<AssignmentStudentCountRow> countStudentsByAssignmentIdIn(
            @Param("assignmentIds") Collection<Long> assignmentIds);
}
