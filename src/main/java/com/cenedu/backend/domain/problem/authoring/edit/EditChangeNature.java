package com.cenedu.backend.domain.problem.authoring.edit;

/** 같은 영역 수정이라도 의미·구조 파급 범위가 다름을 정책에 전달한다. */
public enum EditChangeNature {
    PRESENTATIONAL,
    SEMANTIC,
    STRUCTURAL
}
