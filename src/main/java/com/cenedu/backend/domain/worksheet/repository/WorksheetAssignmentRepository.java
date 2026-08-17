package com.cenedu.backend.domain.worksheet.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
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
}
