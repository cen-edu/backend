package com.cenedu.backend.domain.worksheet.repository;

import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetItemRepository extends JpaRepository<WorksheetItem, Long> {

    /** 지정한 학습지에 해당 문항이 포함됐는지 반환한다. */
    boolean existsByWorksheetIdAndQuestionId(Long worksheetId, Long questionId);
}
