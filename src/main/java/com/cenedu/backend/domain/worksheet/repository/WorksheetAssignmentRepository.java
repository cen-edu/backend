package com.cenedu.backend.domain.worksheet.repository;

import java.util.Collection;
import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;
import com.cenedu.backend.domain.worksheet.repository.row.WorksheetCountRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorksheetAssignmentRepository extends JpaRepository<WorksheetAssignment, Long> {

    /** 학습지의 배포 이력을 배포일 순서대로 조회한다. */
    List<WorksheetAssignment> findAllByWorksheetIdOrderByAssignedAtAsc(Long worksheetId);

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
