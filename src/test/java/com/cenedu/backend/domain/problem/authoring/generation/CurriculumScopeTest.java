package com.cenedu.backend.domain.problem.authoring.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CurriculumScopeTest {
    @Test
    void acceptsTheFixedM1ScopeAndReportsMissingAchievementStandard() {
        CurriculumScope scope = new CurriculumScope("2022_REVISED", "MIDDLE", 1, 2, null, 30L,
                "수와 연산", "정수와 유리수", "정수의 덧셈과 뺄셈");
        assertThat(scope.achievementMissing()).isTrue();
        assertThat(scope.subUnitId()).isEqualTo(30L);
    }

    @Test
    void rejectsScopeOutsideTheAStageCurriculum() {
        assertThatThrownBy(() -> new CurriculumScope("2015_REVISED", "MIDDLE", 1, 1,
                "9수01-01", 30L, "대", "중", "소"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A단계는 2022 개정 중학교 1학년만 지원합니다.");
    }
}
