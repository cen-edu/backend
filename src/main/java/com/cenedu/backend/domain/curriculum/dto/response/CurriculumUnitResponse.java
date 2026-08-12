package com.cenedu.backend.domain.curriculum.dto.response;

import com.cenedu.backend.domain.curriculum.entity.CurriculumUnit;
import com.cenedu.backend.domain.curriculum.entity.enums.UnitLevel;

import java.util.List;

public record CurriculumUnitResponse (
    Long id,
    String name,
    UnitLevel level,
    int displayOrder,
    List<CurriculumUnitResponse> children
){
    public static CurriculumUnitResponse from(CurriculumUnit unit) {
        return new CurriculumUnitResponse(
            unit.getId(),
            unit.getName(),
            unit.getUnitLevel(),
            unit.getDisplayOrder(),
            List.of()
        );
    }

    public CurriculumUnitResponse withChildren(
        List<CurriculumUnitResponse> children
    ) {
        return new CurriculumUnitResponse(
            id,
            name,
            level,
            displayOrder,
            children
        );
    }
}
