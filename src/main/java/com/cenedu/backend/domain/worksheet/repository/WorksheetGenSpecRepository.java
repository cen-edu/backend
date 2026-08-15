package com.cenedu.backend.domain.worksheet.repository;

import java.util.Collection;
import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.WorksheetGenSpec;
import com.cenedu.backend.domain.worksheet.repository.row.WorksheetGenSpecUnitRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorksheetGenSpecRepository extends JpaRepository<WorksheetGenSpec, Long> {

    /** 학습지의 출제 조건을 조회한다. */
    List<WorksheetGenSpec> findAllByWorksheetId(Long worksheetId);

    /** 지정한 학습지들의 소단원 ID를 목록의 unitSummary 조립용으로 조회한다. */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.WorksheetGenSpecUnitRow(
                g.worksheet.id, g.subUnitId)
            from WorksheetGenSpec g
            where g.worksheet.id in :worksheetIds
            order by g.subUnitId asc
            """)
    List<WorksheetGenSpecUnitRow> findUnitRowsByWorksheetIdIn(
            @Param("worksheetIds") Collection<Long> worksheetIds);
}
