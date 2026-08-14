package com.cenedu.backend.domain.worksheet.dto.response;

import com.cenedu.backend.domain.worksheet.entity.WorksheetGenSpec;

/** 복제 프리필의 출제 조건 한 줄. */
public record WorksheetGenSpecItemResponse(
        Long subUnitId,
        String questionType,
        String difficulty,
        short count
) {

    /** 출제 조건 엔티티를 프리필 응답 한 줄로 변환한다. */
    public static WorksheetGenSpecItemResponse from(WorksheetGenSpec genSpec) {
        return new WorksheetGenSpecItemResponse(
                genSpec.getSubUnitId(),
                WorksheetResponseFormatter.toApiQuestionType(genSpec.getQuestionType()),
                WorksheetResponseFormatter.toApiDifficulty(genSpec.getDifficulty()),
                genSpec.getQuestionCount()
        );
    }
}
