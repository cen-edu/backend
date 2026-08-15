package com.cenedu.backend.domain.worksheet.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.repository.row.StudentAssignmentRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorksheetAssignmentStudentRepository
        extends JpaRepository<WorksheetAssignmentStudent, Long> {

    /** 학생 홈 목록. worksheet_assignment_student → assignment → worksheet 축을 한 번에 읽는다. */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.StudentAssignmentRow(
                was.id, w.id, w.title, w.type, w.origin, w.grade, w.semester, was.status,
                was.progressCount, wa.assignedAt, wa.dueAt, was.releasedAt, w.sourceAssignment.id)
            from WorksheetAssignmentStudent was
            join was.assignment wa
            join wa.worksheet w
            where was.studentId = :studentId and w.deletedAt is null
            order by wa.assignedAt desc, was.id desc
            """)
    List<StudentAssignmentRow> findAllForStudent(@Param("studentId") long studentId);

    /** 풀이 진입 상세. 문항은 별도로 배치 조회하므로 여기서는 배정·학습지까지만 조인한다. */
    @Query("""
            select was
            from WorksheetAssignmentStudent was
            join fetch was.assignment wa
            join fetch wa.worksheet w
            where was.id = :assignmentStudentId
            """)
    Optional<WorksheetAssignmentStudent> findDetailById(@Param("assignmentStudentId") long assignmentStudentId);

    /**
     * 맞춤 학습지의 출처 배정-학생 ID를 구한다. {@code worksheet.source_assignment_id}(배정 ID) 목록을
     * 받아 이 학생의 배정-학생 행을 찾는다 — 축이 다르므로 컬럼값을 그대로 못 쓴다(명세 §4).
     */
    List<WorksheetAssignmentStudent> findByStudentIdAndAssignment_IdIn(
            long studentId, Collection<Long> assignmentIds);
}
