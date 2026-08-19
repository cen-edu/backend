package com.cenedu.backend.domain.problem.authoring.generation;

/** 검색·생성·검증이 공유하는 2022 개정 중1 교육과정 범위다. */
public record CurriculumScope(
        String curriculumRevision, String schoolLevel, int grade, Integer semester,
        String achievementStandardId, Long subUnitId, String majorUnitName,
        String middleUnitName, String subUnitName) {
    public CurriculumScope {
        if (!"2022_REVISED".equals(curriculumRevision) || !"MIDDLE".equals(schoolLevel) || grade != 1) {
            throw new IllegalArgumentException("A단계는 2022 개정 중학교 1학년만 지원합니다.");
        }
        if (semester != null && semester != 1 && semester != 2) {
            throw new IllegalArgumentException("학기는 1, 2 또는 null이어야 합니다.");
        }
        if (subUnitId == null || majorUnitName == null || middleUnitName == null || subUnitName == null) {
            throw new IllegalArgumentException("교육과정 단원 경로가 필요합니다.");
        }
        achievementStandardId = achievementStandardId == null || achievementStandardId.isBlank()
                ? null : achievementStandardId.trim();
    }

    public boolean achievementMissing() { return achievementStandardId == null; }
}
