package com.cenedu.backend.global.common.enums;

/**
 * 저장값·API 값은 프론트엔드와 같은 영문 코드를 쓰고, 사람이 읽는 한글은 여기서만 붙인다.
 *
 * <p>한글을 API 값으로 내보내면 표시 문구를 바꿀 때마다 계약이 깨진다. 그래서 코드가 계약이고
 * 한글은 표현이다. 서버가 한글을 붙이는 곳은 교사에게 그대로 전달되는 보고서 문서뿐이고,
 * 화면 라벨은 프론트엔드가 붙인다.
 *
 * <p>여러 도메인이 함께 쓰는 라벨이라 AGENTS.md 3절 3번에 따라 여기 한 번만 선언한다.
 * 코드 값은 프론트 {@code src/mocks/labels.js} 를 따른다.
 */
public final class DisplayLabels {

    private DisplayLabels() {
    }

    /**
     * 난이도 코드 → 하·중·상.
     *
     * <p>이미 생성된 보고서는 감사 목적으로 생성 당시 facts 를 그대로 보존한다. 그 안에는 코드
     * 전환 이전 값(LOW·MEDIUM·HIGH)이 들어 있으므로 함께 읽어 준다. 그러지 않으면 지난 보고서가
     * 전부 "미확인"으로 보인다.
     */
    public static String difficulty(String band) {
        return switch (band == null ? "" : band) {
            case "low", "LOW" -> "하";
            case "mid", "MEDIUM" -> "중";
            case "high", "HIGH" -> "상";
            default -> "미확인";
        };
    }

    /**
     * 문항 평가 영역 코드 → 이해·계산·추론·문제해결.
     *
     * <p>AIHub 110·111 원본의 {@code question_sector1} 에서 온다. 국가 평가 체계가 쓰는 행동 영역
     * 이름을 그대로 표시한다. 현재 저장 코드 {@code UNDERSTANDING}의 표기는 "이해"다. 이전 코드
     * {@code concept}과 한글 값도 보존된 보고서를 읽기 위해 함께 지원한다.
     *
     * <p>풀이 단계 축(INTERPRET·MODEL·EXECUTE·ANSWER)과는 다른 축이므로 섞지 않는다. 단계 축은
     * 문항이 아니라 풀이 구간마다 붙고, 한 문항 안에서 여러 단계가 나온다. 행동 영역에는 순서가
     * 없고 단계 축은 순서가 전제다.
     *
     * <p>보존된 보고서의 한글 값도 함께 읽는다. 생성 당시 facts 가 그대로 남아 있다.
     */
    public static String area(String area) {
        return switch (area == null ? "" : area) {
            case "UNDERSTANDING", "concept", "개념", "이해" -> "이해";
            case "CALCULATION", "calculation", "계산" -> "계산";
            case "REASONING", "reasoning", "추론" -> "추론";
            case "PROBLEM_SOLVING", "problemSolving", "문제해결", "문제 해결" -> "문제해결";
            default -> "미분류";
        };
    }

    /**
     * 학습 상태 코드 → 보고서 표기.
     *
     * <p>한 번의 관찰을 확정된 판정처럼 읽히지 않게 하는 문구를 쓴다.
     */
    public static String status(String status) {
        return switch (status == null ? "" : status) {
            case "stable" -> "현재 확인 기준 충족";
            case "review" -> "추가 확인 필요";
            case "priority" -> "집중 지도 필요";
            case "insufficient" -> "자료 부족";
            default -> "확인 필요";
        };
    }
}
