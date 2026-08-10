package com.cenedu.backend.domain.analysis.reissue;

/**
 * 문항 난이도. 원본 라벨을 세 칸으로 읽는 단 하나의 자리다.
 *
 * <p><b>110 원본 숫자는 작을수록 어렵다.</b> 중1 라벨 4,159문항 전수에서 확인한 값이다.
 *
 * <table>
 *   <caption>110 원본 난이도별 실측</caption>
 *   <tr><th>question_difficulty</th><th>문항 수</th><th>평균 정답률</th><th>평균 풀이시간</th></tr>
 *   <tr><td>1 (상)</td><td>1,038</td><td>67.9%</td><td>178초</td></tr>
 *   <tr><td>2 (중)</td><td>2,104</td><td>85.0%</td><td>138초</td></tr>
 *   <tr><td>3 (하)</td><td>1,017</td><td>94.1%</td><td>103초</td></tr>
 * </table>
 *
 * <p>읽는 방향이 직관과 반대라, 숫자를 그대로 비교하는 코드를 두지 않는다. 난이도를 낮춘다는
 * 것은 원본 숫자를 올린다는 뜻이라 부호를 한 번만 잘못 잡아도 막힌 학생에게 더 어려운 문항이
 * 나간다. 그 실수는 화면에 아무 증상도 남기지 않는다.
 *
 * <p>이 클래스는 원래 problem 도메인에 있어야 할 개념이다. 그 도메인이 아직 비어 있고
 * AGENTS.md 2절이 남의 패키지에 파일을 만들지 않도록 하고 있어 여기 둔다. 문제은행이
 * 생기면 그쪽으로 옮긴다.
 */
public enum QuestionDifficulty {

    HIGH("high", "1"),
    MEDIUM("mid", "2"),
    LOW("low", "3");

    private final String band;
    private final String sourceLabel;

    QuestionDifficulty(String band, String sourceLabel) {
        this.band = band;
        this.sourceLabel = sourceLabel;
    }

    /** API·DB가 쓰는 코드. {@code difficultyBand} 값이다. */
    public String band() {
        return band;
    }

    /**
     * 110 원본 라벨 숫자. <b>서비스 난이도와 방향이 반대다.</b>
     *
     * @see #serviceDifficulty()
     */
    public String sourceLabel() {
        return sourceLabel;
    }

    /**
     * 문제은행 {@code question.difficulty}에 넣는 값. <b>1이 하, 3이 상이다.</b>
     *
     * <p>110 원본과 방향이 정반대라 이 자리에서 반드시 뒤집어야 한다.
     *
     * <table>
     *   <caption>원천별 난이도 변환</caption>
     *   <tr><th></th><th>하</th><th>중</th><th>상</th></tr>
     *   <tr><td>110 원본 {@code questionDifficulty}</td><td>3</td><td>2</td><td>1</td></tr>
     *   <tr><td>30 원본 라벨</td><td>하</td><td>중</td><td>상</td></tr>
     *   <tr><td><b>서비스 {@code question.difficulty}</b></td><td><b>1</b></td><td>2</td><td><b>3</b></td></tr>
     * </table>
     *
     * <p>30번은 하→1로 그대로 가고 110번만 뒤집힌다. 두 원천을 같은 컬럼에 넣으므로 한쪽만
     * 잘못 잡으면 정반대 난이도가 섞이고, 그 상태에서는 난이도로 거른 어떤 결과도 믿을 수 없다.
     */
    public short serviceDifficulty() {
        return switch (this) {
            case LOW -> (short) 1;
            case MEDIUM -> (short) 2;
            case HIGH -> (short) 3;
        };
    }

    /** 문제은행 {@code question.difficulty}를 읽는다. 1~3 밖이면 {@code null}이다. */
    public static QuestionDifficulty fromServiceDifficulty(int value) {
        return switch (value) {
            case 1 -> LOW;
            case 2 -> MEDIUM;
            case 3 -> HIGH;
            default -> null;
        };
    }

    /**
     * 원본 난이도 라벨을 읽는다. 모르는 값이면 {@code null}이다.
     *
     * <p>없는 값을 중간값으로 채우지 않는다. 난이도를 모르는 문항과 중 난이도 문항은 다르고,
     * 둘을 합치면 난이도별 정답률이 조용히 흐려진다.
     */
    public static QuestionDifficulty fromSourceLabel(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return switch (normalized) {
            case "1", "상", "HIGH", "GPT_HIGH" -> HIGH;
            case "2", "중", "MID", "MEDIUM", "GPT_MID" -> MEDIUM;
            case "3", "하", "LOW", "GPT_LOW" -> LOW;
            default -> null;
        };
    }

    /** 저장된 {@code difficultyBand} 코드를 읽는다. 모르는 값이면 {@code null}이다. */
    public static QuestionDifficulty fromBand(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return switch (normalized) {
            case "HIGH" -> HIGH;
            case "MID", "MEDIUM" -> MEDIUM;
            case "LOW" -> LOW;
            default -> null;
        };
    }

    /**
     * 한 칸 쉬운 쪽으로. 이미 가장 쉬우면 그대로다.
     *
     * <p>누적 오류로 지원이 필요해진 학생에게 같은 난이도를 계속 주지 않기 위한 이동이다.
     */
    public QuestionDifficulty easier() {
        return switch (this) {
            case HIGH -> MEDIUM;
            case MEDIUM, LOW -> LOW;
        };
    }

    /** 한 칸 어려운 쪽으로. 이미 가장 어려우면 그대로다. */
    public QuestionDifficulty harder() {
        return switch (this) {
            case LOW -> MEDIUM;
            case MEDIUM, HIGH -> HIGH;
        };
    }
}
