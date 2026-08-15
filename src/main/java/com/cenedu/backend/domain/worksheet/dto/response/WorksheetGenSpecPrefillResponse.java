package com.cenedu.backend.domain.worksheet.dto.response;

import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.Worksheet;

/** 학습지 복제 화면의 프리필 응답. */
public record WorksheetGenSpecPrefillResponse(
        Long sourceWorksheetId,
        String type,
        short grade,
        String semester,
        List<WorksheetGenSpecItemResponse> genSpec
) {

    /** 학습지와 출제 조건 목록을 복제 프리필 응답으로 변환한다. */
    public static WorksheetGenSpecPrefillResponse from(
            Worksheet worksheet, List<WorksheetGenSpecItemResponse> genSpec
    ) {
        return new WorksheetGenSpecPrefillResponse(
                worksheet.getId(),
                WorksheetResponseFormatter.toApiType(worksheet.getType()),
                worksheet.getGrade(),
                WorksheetResponseFormatter.toApiSemester(worksheet.getSemester()),
                List.copyOf(genSpec)
        );
    }
}
