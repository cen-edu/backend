package com.cenedu.backend.domain.curriculum.dto.response;

import com.cenedu.backend.domain.curriculum.entity.CurriculumUnit;

public record CurriculumPathResponse(
    Long majorUnitId,
    String majorUnitName,
    Long middleUnitId,
    String middleUnitName,
    Long subUnitId,
    String subUnitName
    ,String curriculumRevision,
    String schoolLevel,
    short grade,
    Short semester,
    String achievementStandardId
) {

    /**
     * 대단원·중단원·소단원을 문항 표시용 단원 경로로 변환한다.
     */
    public static CurriculumPathResponse from(
        CurriculumUnit majorUnit,
        CurriculumUnit middleUnit,
        CurriculumUnit subUnit
    ) {
        return new CurriculumPathResponse(
            majorUnit.getId(),
            majorUnit.getName(),
            middleUnit.getId(),
            middleUnit.getName(),
            subUnit.getId(),
            subUnit.getName(),
            subUnit.getCurriculumRevision(), subUnit.getSchoolLevel(), subUnit.getGrade(),
            subUnit.getSemester(), subUnit.getAchievementStandardId()
        );
    }
}
