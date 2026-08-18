package com.cenedu.backend.domain.problem.authoring.edit;

/** 확정된 교사 요청을 부분 수정·이전 버전 복원·전체 교체 중 하나로 분류한다. */
public enum EditAction {
    MODIFY,
    RESTORE,
    REPLACE
}
