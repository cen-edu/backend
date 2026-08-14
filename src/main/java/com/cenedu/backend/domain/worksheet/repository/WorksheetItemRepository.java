package com.cenedu.backend.domain.worksheet.repository;

import java.util.Collection;
import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.repository.row.WorksheetCountRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorksheetItemRepository extends JpaRepository<WorksheetItem, Long> {

    /** 지정한 학습지에 해당 문항이 포함됐는지 반환한다. */
    boolean existsByWorksheetIdAndQuestionId(Long worksheetId, Long questionId);

    /** 학습지 문항을 표시 순서대로 조회한다. */
    List<WorksheetItem> findAllByWorksheetIdOrderByDisplayOrderAsc(Long worksheetId);

    /** 지정한 학습지들의 문항 수를 학습지 ID별로 센다. */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.WorksheetCountRow(
                i.worksheet.id, count(i))
            from WorksheetItem i
            where i.worksheet.id in :worksheetIds
            group by i.worksheet.id
            """)
    List<WorksheetCountRow> countByWorksheetIdIn(@Param("worksheetIds") Collection<Long> worksheetIds);
}
