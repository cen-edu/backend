package com.cenedu.backend.domain.dashboard.entity.enums;

/**
 * 학습지 한 배정의 채점·확정 단계.
 *
 * <p>{@code DashboardAssignmentStatus}(진행·완료·기한초과)와 축이 다르다. 그쪽은 학생들이 얼마나
 * 풀었는지고, 이쪽은 <b>교사가 결과를 어디까지 처리했는지</b>다. 종합평가 화면이 "채점 중"과
 * "채점 완료"와 "결과 확정"을 다른 버튼으로 다루기 때문에 둘을 합칠 수 없다.
 */
public enum DashboardResultStatus {

    /** 제출은 있으나 채점이 끝나지 않았다. */
    GRADING,

    /** 제출자 전원 채점이 끝났고 아직 확정 전이다. 학생은 점수·정답·해설을 볼 수 없다. */
    GRADED,

    /** 교사가 반 단위 확정을 눌렀다. 이때부터 학생에게 결과가 공개된다. */
    RELEASED
}
