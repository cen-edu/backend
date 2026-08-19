package com.cenedu.backend.domain.worksheet.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
import com.cenedu.backend.domain.worksheet.repository.row.AssignmentStudentCountRow;
import com.cenedu.backend.domain.worksheet.repository.row.CustomLearningWorksheetCountRow;
import com.cenedu.backend.domain.worksheet.repository.row.CustomLearningStudentCountRow;
import com.cenedu.backend.domain.worksheet.repository.row.CustomLearningAssignmentRow;
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
     * 그 배정이 바로 그 학습지의 배포인지 반환한다. 1차 맞춤을 만들 때
     * parentWorksheetId 가 sourceAssignmentId 의 학습지와 같은지 확인하는 데 쓴다.
     */
    boolean existsByIdAndWorksheetId(Long id, Long worksheetId);

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

    /**
     * 원본 배정 여러 개에서 파생된 맞춤 배정을 읽는다. 학습 현황과 평가 결과가 같은 쿼리를 써야
     * 두 화면이 같은 집합을 본다.
     *
     * <p><b>차수는 이 정렬로 파생하지 않는다.</b> {@code parentWorksheetId} 체인의 깊이로 매긴다 —
     * 배정일 순으로 매기면 늦게 받은 학생의 첫 맞춤이 3차로 보인다. 정렬은 화면 축(학생→배정일)에
     * 맞춘 것이고, 차수 계산은 {@code CustomSessionNumbering}이 한다.
     *
     * <p>{@code origin}과 {@code deletedAt}을 함께 건다. {@code source_assignment_id}만으로 거르면
     * 소프트 삭제된 맞춤 학습지가 화면에 남는다 — analysis 쪽 맞춤 조회와 같은 필터 조합이라야
     * 두 화면이 같은 집합을 본다.
     */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.CustomLearningAssignmentRow(
                sa.id, sa.worksheet.id, w.id, w.parentWorksheet.id,
                w.title, w.type, ca.id, ca.studentId, ca.assignedAt, ca.dueAt)
            from WorksheetAssignment ca
            join ca.worksheet w
            join w.sourceAssignment sa
            where sa.id in :sourceAssignmentIds
              and w.origin = com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin.CUSTOM
              and w.deletedAt is null
            order by sa.id asc, ca.studentId asc, ca.assignedAt asc, ca.id asc
            """)
    List<CustomLearningAssignmentRow> findCustomLearningAssignments(
            @Param("sourceAssignmentIds") Collection<Long> sourceAssignmentIds);

    /**
     * 원본 배정 여러 개의 맞춤 학습지 축 집계를 한 번에 낸다. 배정마다 부르면 목록 길이만큼 쿼리가 든다.
     *
     * <p>학생 행과 조인하지 않는다. 조인하면 학습지 하나가 학생 수만큼 늘어나(팬아웃) 집계에
     * distinct 를 겹쳐 써야 한다. 학생 축은 별도 쿼리로 센다.
     */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.CustomLearningWorksheetCountRow(
                sa.id,
                count(distinct w.id),
                count(distinct case when w.type
                        = com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType.COMPREHENSIVE_ASSESSMENT
                    then w.id else null end),
                max(ca.assignedAt),
                max(ca.dueAt))
            from WorksheetAssignment ca
            join ca.worksheet w
            join w.sourceAssignment sa
            where sa.id in :sourceAssignmentIds
              and w.origin = com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin.CUSTOM
              and w.deletedAt is null
            group by sa.id
            """)
    List<CustomLearningWorksheetCountRow> summarizeCustomLearningWorksheets(
            @Param("sourceAssignmentIds") Collection<Long> sourceAssignmentIds);

    /**
     * 원본 배정 여러 개의 맞춤 학습 학생 축 집계.
     *
     * <p>진행 상태 파생은 {@code summarizeLearningStatus}와 같은 규칙이되 <b>맞춤 배정 자신의
     * 마감</b>을 본다. 마감 조건을 미시작·풀이중 양쪽에 걸어야 미제출과 이중으로 세지 않는다.
     * 채점 집계는 종합평가 학습지로만 한정한다 — 일반 학습은 채점 상태 자체가 없다.
     */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.CustomLearningStudentCountRow(
                sa.id,
                count(distinct was.studentId),
                coalesce(sum(case when was.status
                        = com.cenedu.backend.global.common.enums.AssignmentStatus.NOT_STARTED
                      and was.progressCount = 0 and ca.dueAt > :now then 1L else 0L end), 0L),
                coalesce(sum(case when was.status
                        = com.cenedu.backend.global.common.enums.AssignmentStatus.NOT_STARTED
                      and was.progressCount > 0 and ca.dueAt > :now then 1L else 0L end), 0L),
                coalesce(sum(case when was.status in (
                        com.cenedu.backend.global.common.enums.AssignmentStatus.SUBMITTED,
                        com.cenedu.backend.global.common.enums.AssignmentStatus.GRADED)
                    then 1L else 0L end), 0L),
                coalesce(sum(case when was.status
                        = com.cenedu.backend.global.common.enums.AssignmentStatus.NOT_SUBMITTED
                      or (was.status
                        = com.cenedu.backend.global.common.enums.AssignmentStatus.NOT_STARTED
                      and ca.dueAt <= :now) then 1L else 0L end), 0L),
                coalesce(sum(case when w.type
                        = com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType.COMPREHENSIVE_ASSESSMENT
                      and was.gradedAt is null and was.submittedAt is not null then 1L else 0L end), 0L),
                coalesce(sum(case when w.type
                        = com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType.COMPREHENSIVE_ASSESSMENT
                      and was.gradedAt is not null then 1L else 0L end), 0L))
            from WorksheetAssignmentStudent was
            join was.assignment ca
            join ca.worksheet w
            join w.sourceAssignment sa
            where sa.id in :sourceAssignmentIds
              and w.origin = com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin.CUSTOM
              and w.deletedAt is null
            group by sa.id
            """)
    List<CustomLearningStudentCountRow> summarizeCustomLearningStudents(
            @Param("sourceAssignmentIds") Collection<Long> sourceAssignmentIds,
            @Param("now") OffsetDateTime now);
}
