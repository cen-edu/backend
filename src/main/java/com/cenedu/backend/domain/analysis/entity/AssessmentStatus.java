package com.cenedu.backend.domain.analysis.entity;

/**
 * 문제지 한 회분의 진행 상태.
 *
 * <p>프론트 {@code labels.js} 의 진행 상태(not-started / in-progress / submitted)와는 별개다.
 * 그쪽은 화면에 뿌리는 라벨이고, 이 값은 "풀이를 더 받을 것인가"를 정하는 저장 상태다.
 * 완료된 회차는 풀이를 더 받지 않는다.
 */
public enum AssessmentStatus {

    IN_PROGRESS,
    COMPLETED;

    public boolean completed() {
        return this == COMPLETED;
    }
}
