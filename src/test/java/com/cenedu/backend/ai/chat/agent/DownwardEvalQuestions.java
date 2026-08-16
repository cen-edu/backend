package com.cenedu.backend.ai.chat.agent;

import java.util.List;

/**
 * 하향 탐색 평가 세트 (c) <b>v2</b>. 문항과 정답표만 담는다.
 *
 * <h2>v2 변경 내역 (task_24b §0-5) — 여기 적힌 것 외에는 손대지 않았다</h2>
 *
 * v1 은 한 번 돌았을 뿐 baseline 으로 굳지 않았다. 굳기 전에 아래 셋을 바로잡는다.
 *
 * <ol>
 *   <li><b>{@code DC3-2} 내부 모순</b> — 주석은 "앵커는 새 질문이 정한다"인데 기대 앵커가
 *       직전 앵커 {@code 교각}(4)이었다. 주석대로 {@code 맞꼭지각}(16)으로 고쳤다.
 *   <li><b>{@code DA3-2} 기대 상태</b> — "그게 왜 그렇게 되는 거예요?" 의 기대가
 *       {@code SLIGHTLY_EASIER} 였다. task_24b §0-3 이 <b>"왜" 질문은 하향 신호가 아니라고
 *       확정</b>했으므로 {@code NONE}(이동 없음)으로 바꿨다. <b>이 한 턴이 뒤 세 턴을 끌고 간다</b> —
 *       DA3 는 1칸 체인(54 → 161 → 159 → 80)으로 엮여 있어서, 2번 턴이 안 움직이면
 *       {@code DA3-3}·{@code DA3-4}·{@code DA3-5} 의 기대 앵커가 한 칸씩 밀린다
 *       (159→161 · 159→161 · 80→159). 밀린 값은 task_20 전수표에서 다시 읽었다.
 *       <b>문항(발화)은 넷 다 그대로이고 기대값만 움직였다.</b>
 *   <li><b>폴백 시나리오 {@code DE1} 신설</b> — v1 30발화에서 {@code FALLBACK_STEP_DOWN} 이
 *       0회라 그 경로가 사는지 죽었는지 알 수 없었다.
 * </ol>
 *
 * <p><b>연속 하강 유형은 v1·v2 모두 초등에 닿지 않는다.</b> DA1 은 2, DA2 는 60, DA3 는
 * (v1) 80 · (v2) 159 에서 끝나는데 <b>전부 {@code MIDDLE_1}</b> 이다. task_22 §3-1 이 이 유형의
 * 앵커를 "중1 안에서 1순위로 3칸 이상"으로 골랐기 때문이며, 따라서 <b>대역 전환을 재는 것은
 * 건너뛰기 3턴뿐</b>이다. v2 가 만든 손실이 아니라 원래 없던 것이다.
 *
 * <p><b>기존 39턴 세트({@link EvalQuestions})와 완전히 분리된 세트다.</b> 합치면 분모가 바뀌어
 * task_08~18 전체와 비교가 끊긴다. 상속하지도 재사용하지도 않는다.
 *
 * <p><b>러너가 없다.</b> 하향 탐색 기능이 아직 없으므로 실행할 수 없고, 러너 형태는 구현 시점에
 * 정해진다. 세트를 구현보다 먼저 만드는 이유는, 나중에 만들면 만들어진 동작에 맞춰 문항을 쓰게
 * 되고 그것은 자를 결과에 맞추는 일이기 때문이다.
 *
 * <h2>채점 축 — 기대 앵커 일치</h2>
 *
 * <b>실제 앵커가 {@link Turn#expectedAnchorId()} 와 같은 개념이면 통과다.</b> 이름이 아니라 id로
 * 본다 — 동명 노드가 11쌍 있어 이름으로는 갈리지 않는다.
 *
 * <p><b>"앵커의 학년값이 낮아졌는가"를 쓰지 않는다.</b> 그 축은 이 세트에서 성립하지 않는다.
 * 연속 하강은 중1 안에서 움직이는데 학년 축이 {@code MIDDLE_1} 을 14로 고정하므로(설계 v2 §2-1),
 * 기능이 완벽히 동작해도 학년값이 정의상 변하지 않는다. {@link Turn#expectedGrade()} 는 참고용으로
 * 남겨 두었을 뿐 <b>판정에 쓰지 않는다.</b>
 *
 * <h2>정답표의 기준 규칙 (설계 확정본 v2 §2-1 · §2-4 · §2-5)</h2>
 *
 * 규칙이 바뀌면 이 세트도 함께 무효가 된다.
 *
 * <pre>
 * 학년값     MIDDLE_1 = 14 (source_semester 를 읽지 않는다)
 *           ELEMENTARY = 초1-1학기 1 … 초6-2학기 12
 * 정렬       학년값 DESC, id ASC
 * 1칸        직접 선수(1홉) 중 1순위 greedy
 * 건너뛰기    BFS 최단 ELEMENTARY. 같은 홉이면 위 정렬로 하나
 * </pre>
 *
 * <p><b>전제 — 갈래 선택이 결정론이다.</b> 이 정답표는 동점 갈래에서 {@code id} 오름차순 1순위가
 * 선택된다고 본다. 설계 v2 §4-1의 LLM 갈래 선택을 나중에 도입하면 동점 68건에서 기대 앵커가
 * 흔들릴 수 있으므로 <b>세트를 재검토해야 한다.</b>
 *
 * <h2>음성 문항이 유일한 통제 장치다</h2>
 *
 * 채점 축이 하나뿐이라 "무조건 내려가기" 구현을 막는 것은 {@link Category#NEGATIVE} 뿐이다.
 * 18턴 중 6턴(33%)이며, 그중 둘은 <b>연속 하강 시나리오 안에 삽입</b>돼 있다 — 하강 모드에 들어간
 * 뒤 빠져나올 수 있는지를 보기 위해서다. 음성 턴에서는 이동이 없으므로 <b>그다음 턴의 기대 앵커는
 * 음성 턴 직전 앵커에서 한 칸</b>이다.
 */
final class DownwardEvalQuestions {

    private DownwardEvalQuestions() {
    }

    /** 문항 유형. */
    enum Category {
        /** 중1 안에서 1칸씩 연속으로 내려간다. */
        DESCENT,
        /** 한 번에 최단 초등 개념으로 건너뛴다. */
        SKIP,
        /**
         * 건너뛰기를 요청받았지만 초등까지 가는 경로가 없어 한 칸으로 내려간다.
         *
         * <p>{@link #SKIP} 과 나눈 이유는 두 유형이 <b>다른 코드 경로</b>를 타기 때문이다.
         * 합쳐 두면 "건너뛰기가 됐다"와 "폴백이 걸렸다"가 같은 칸에서 세어져,
         * 폴백이 죽어 있어도 지표에 드러나지 않는다.
         */
        FALLBACK,
        /** 이동하면 안 되는 턴. 이 세트의 통제 장치다. */
        NEGATIVE,
        /** 선수가 없어 내려갈 곳이 없다. */
        DEAD_END
    }

    /** LLM 호출 ①이 판정해야 하는 학생 상태. 코드가 이것을 이동 거리로 환산한다. */
    enum MoveState {
        /** 이동 없음. */
        NONE,
        /** 1순위로 한 칸. */
        SLIGHTLY_EASIER,
        /** 최단 초등 개념으로 점프. */
        MUCH_EASIER
    }

    /** 채점 기준. */
    enum Verdict {
        /** 실제 앵커가 기대 앵커와 같은 개념(id)이어야 한다. */
        ANCHOR_MATCHES,
        /**
         * 앵커가 이동하지 않고 물러서는 응답이 나와야 한다.
         *
         * <p><b>"이동 없음"까지만 기계 판정한다.</b> 물러섬 문구는 설계 v2 §4-2에서 미정이므로
         * 문구 판정 기준은 채점 시점에 정한다.
         */
        CANNOT_MOVE
    }

    /**
     * @param scored           첫 턴(앵커 확정용)은 채점하지 않는다
     * @param expectedAnchorId 기대 앵커의 {@code chat_concept.id}. 이동이 없으면 직전 앵커와 같다
     * @param expectedGrade    <b>참고용이며 판정에 쓰지 않는다</b> (클래스 주석 참조)
     * @param source           기대 앵커의 근거. 추정으로 채우지 않는다
     */
    record Turn(
            String id,
            String utterance,
            boolean scored,
            MoveState expectedState,
            long priorAnchorId,
            long expectedAnchorId,
            String expectedAnchor,
            int expectedGrade,
            Verdict verdict,
            String source
    ) {
    }

    record Scenario(String id, Category category, long startAnchorId, String startAnchor,
                    List<Turn> turns) {
    }

    private static final String SRC_RANK = "task_20 rank1.tsv (1순위 전수표)";
    private static final String SRC_LAND = "task_22 부록 BFS 최단 착지 재계산";
    private static final String SRC_NONE = "task_20/21 선수 0개 19건 목록";
    private static final String SRC_FIRST = "앵커 확정용, 채점 제외";
    private static final String SRC_HOLD = "음성 — 이동 없음이 통과 조건";
    private static final String SRC_WHY = "v2 — \"왜\" 질문은 하향 신호가 아니다 (task_24b §0-3)";
    private static final String SRC_TOPIC = "v2 — 화제 전환이므로 새 질문이 앵커를 정한다";
    private static final String SRC_FALLBACK =
            "task_23 경로 없음 10건 + task_20 rank1.tsv — 착지가 없어 1칸으로 폴백";

    static final List<Scenario> ALL = List.of(

            // ── 연속 하강 ────────────────────────────────────────────────
            // 음성 턴(DA1-3)이 중간에 끼어 있다. 그 다음 턴은 DA1-3 직전 앵커에서 한 칸이다.
            new Scenario("DA1", Category.DESCENT, 13L, "두 평면의 수직", List.of(
                    new Turn("DA1-1", "두 평면의 수직이 뭐예요?", false,
                            MoveState.NONE, 13L, 13L, "두 평면의 수직", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    new Turn("DA1-2", "무슨 말인지 모르겠어요", true,
                            MoveState.SLIGHTLY_EASIER, 13L, 3L,
                            "공간에서 직선과 평면의 위치 관계", 14,
                            Verdict.ANCHOR_MATCHES, SRC_RANK),
                    new Turn("DA1-3", "아 그건 알아요", true,
                            MoveState.NONE, 3L, 3L,
                            "공간에서 직선과 평면의 위치 관계", 14,
                            Verdict.ANCHOR_MATCHES, SRC_HOLD + " (하강 중간 이탈)"),
                    new Turn("DA1-4", "근데 그 앞부분이 헷갈려요", true,
                            MoveState.SLIGHTLY_EASIER, 3L, 2L,
                            "공간에서 두 직선의 위치 관계", 14,
                            Verdict.ANCHOR_MATCHES, SRC_RANK))),

            new Scenario("DA2", Category.DESCENT, 88L, "이항", List.of(
                    new Turn("DA2-1", "이항이 뭐예요?", false,
                            MoveState.NONE, 88L, 88L, "이항", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    new Turn("DA2-2", "이해가 안 돼요", true,
                            MoveState.SLIGHTLY_EASIER, 88L, 84L,
                            "등식과 좌변, 우변, 양변", 14,
                            Verdict.ANCHOR_MATCHES, SRC_RANK),
                    new Turn("DA2-3", "아직도 잘 모르겠어요", true,
                            MoveState.SLIGHTLY_EASIER, 84L, 60L,
                            "문자를 사용한 식 나타내기", 14,
                            Verdict.ANCHOR_MATCHES, SRC_RANK))),

            // 음성 턴(DA3-4) 삽입. 그 다음 턴은 DA3-4 직전 앵커에서 한 칸이다.
            //
            // v2 — DA3-2 를 NONE 으로 바로잡으면서 뒤 세 턴이 한 칸씩 밀렸다. 클래스 주석의
            // "v2 변경 내역" 참조. 밀린 기대 앵커는 task_20 전수표에서 다시 읽었다
            // (54 → 161 → 159, 그 아래가 80).
            new Scenario("DA3", Category.DESCENT, 54L, "나눗셈 기호의 생략", List.of(
                    new Turn("DA3-1", "나눗셈 기호는 언제 생략해요?", false,
                            MoveState.NONE, 54L, 54L, "나눗셈 기호의 생략", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    // "왜" 질문은 이유를 원하는 것이지 더 쉬운 설명을 원하는 것이 아니다.
                    new Turn("DA3-2", "그게 왜 그렇게 되는 거예요?", true,
                            MoveState.NONE, 54L, 54L,
                            "나눗셈 기호의 생략", 14,
                            Verdict.ANCHOR_MATCHES, SRC_WHY),
                    new Turn("DA3-3", "역수가 뭔지도 헷갈려요", true,
                            MoveState.SLIGHTLY_EASIER, 54L, 161L,
                            "역수를 이용한 나눗셈", 14,
                            Verdict.ANCHOR_MATCHES, SRC_RANK),
                    new Turn("DA3-4", "그건 이해했어요", true,
                            MoveState.NONE, 161L, 161L,
                            "역수를 이용한 나눗셈", 14,
                            Verdict.ANCHOR_MATCHES, SRC_HOLD + " (하강 중간 이탈)"),
                    new Turn("DA3-5", "그래도 아직 어려워요", true,
                            MoveState.SLIGHTLY_EASIER, 161L, 159L,
                            "부호가 다른 두 수의 곱셈", 14,
                            Verdict.ANCHOR_MATCHES, SRC_RANK))),

            // ── 건너뛰기 ─────────────────────────────────────────────────
            new Scenario("DB1", Category.SKIP, 154L, "정비례, 반비례 관계의 활용", List.of(
                    new Turn("DB1-1", "정비례랑 반비례를 문제에 어떻게 써요?", false,
                            MoveState.NONE, 154L, 154L, "정비례, 반비례 관계의 활용", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    new Turn("DB1-2", "훨씬 쉬운 것부터 알려주세요", true,
                            MoveState.MUCH_EASIER, 154L, 267L,
                            "대응 관계를 기호를 사용하여 식으로 나타내는 방법", 9,
                            Verdict.ANCHOR_MATCHES, SRC_LAND + " — 7홉"))),

            new Scenario("DB2", Category.SKIP, 99L, "각기둥", List.of(
                    new Turn("DB2-1", "각기둥이 뭐예요?", false,
                            MoveState.NONE, 99L, 99L, "각기둥", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    new Turn("DB2-2", "기초부터 알려주세요", true,
                            MoveState.MUCH_EASIER, 99L, 250L, "평면도형 밀기", 7,
                            Verdict.ANCHOR_MATCHES, SRC_LAND + " — 4홉"))),

            new Scenario("DB3", Category.SKIP, 1L, "각", List.of(
                    new Turn("DB3-1", "각이 뭐예요?", false,
                            MoveState.NONE, 1L, 1L, "각", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    new Turn("DB3-2", "훨씬 기초부터 다시 알려주세요", true,
                            MoveState.MUCH_EASIER, 1L, 246L, "각의 크기", 7,
                            Verdict.ANCHOR_MATCHES, SRC_LAND + " — 1홉"))),

            // ── 폴백 (건너뛰기를 요청받았지만 착지가 없다) ────────────────
            //
            // 시작 앵커 선정: task_23 이 낸 "경로 없음 10건"(4·5·20·40·41·42·48·49·165·208) 중
            // id 오름차순으로, ① 이미 다른 시나리오의 시작 앵커인 것(4·5)과
            // ② 폴백 착지가 다른 시나리오의 시작 앵커와 겹치는 것(20 → 9 = DD2 시작)을 건너뛰어 40.
            // 겹치면 폴백이 걸린 것인지 다른 데서 온 것인지 판독이 흐려진다.
            new Scenario("DE1", Category.FALLBACK, 40L, "중앙값", List.of(
                    new Turn("DE1-1", "중앙값이 뭐예요?", false,
                            MoveState.NONE, 40L, 40L, "중앙값", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    new Turn("DE1-2", "제일 아래쪽 개념부터 알려주세요", true,
                            MoveState.MUCH_EASIER, 40L, 39L, "대푯값", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FALLBACK))),

            // ── 음성 (이동하면 안 된다) ───────────────────────────────────
            new Scenario("DC1", Category.NEGATIVE, 2L, "공간에서 두 직선의 위치 관계", List.of(
                    new Turn("DC1-1", "공간에서 두 직선의 위치 관계가 뭐예요?", false,
                            MoveState.NONE, 2L, 2L, "공간에서 두 직선의 위치 관계", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    new Turn("DC1-2", "아 이제 알겠어요", true,
                            MoveState.NONE, 2L, 2L, "공간에서 두 직선의 위치 관계", 14,
                            Verdict.ANCHOR_MATCHES, SRC_HOLD))),

            new Scenario("DC2", Category.NEGATIVE, 3L, "공간에서 직선과 평면의 위치 관계", List.of(
                    new Turn("DC2-1", "공간에서 직선과 평면은 어떤 관계가 있어요?", false,
                            MoveState.NONE, 3L, 3L, "공간에서 직선과 평면의 위치 관계", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    new Turn("DC2-2", "이해했어요, 고맙습니다", true,
                            MoveState.NONE, 3L, 3L, "공간에서 직선과 평면의 위치 관계", 14,
                            Verdict.ANCHOR_MATCHES, SRC_HOLD))),

            new Scenario("DC3", Category.NEGATIVE, 4L, "교각", List.of(
                    new Turn("DC3-1", "교각이 뭐예요?", false,
                            MoveState.NONE, 4L, 4L, "교각", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    // 화제 전환. 하향 신호가 아니라 새 개념 질문이다. 앵커는 새 질문이 정한다.
                    // v2 — 기대 앵커가 주석과 어긋나 있었다(직전 앵커 교각). 주석대로 고쳤다.
                    new Turn("DC3-2", "그럼 맞꼭지각은 뭐예요?", true,
                            MoveState.NONE, 4L, 16L, "맞꼭지각", 14,
                            Verdict.ANCHOR_MATCHES, SRC_TOPIC))),

            new Scenario("DC4", Category.NEGATIVE, 5L, "교선", List.of(
                    new Turn("DC4-1", "교선이 뭐예요?", false,
                            MoveState.NONE, 5L, 5L, "교선", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    // 재설명 요청. 같은 개념을 다시 듣고 싶다는 것이지 더 쉬운 것을 달라는 게 아니다.
                    new Turn("DC4-2", "방금 설명해 준 거 한 번만 더 말해 주세요", true,
                            MoveState.NONE, 5L, 5L, "교선", 14,
                            Verdict.ANCHOR_MATCHES, SRC_HOLD + " (재설명 요청)"))),

            // ── 갈 곳 없음 ───────────────────────────────────────────────
            new Scenario("DD1", Category.DEAD_END, 6L, "교점", List.of(
                    new Turn("DD1-1", "교점이 뭐예요?", false,
                            MoveState.NONE, 6L, 6L, "교점", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    new Turn("DD1-2", "훨씬 쉬운 거부터 알려주세요", true,
                            MoveState.MUCH_EASIER, 6L, 6L, "교점", 14,
                            Verdict.CANNOT_MOVE, SRC_NONE + " — 선수 0개, BFS 착지도 없음"))),

            new Scenario("DD2", Category.DEAD_END, 9L, "두 점 사이의 거리", List.of(
                    new Turn("DD2-1", "두 점 사이의 거리는 어떻게 구해요?", false,
                            MoveState.NONE, 9L, 9L, "두 점 사이의 거리", 14,
                            Verdict.ANCHOR_MATCHES, SRC_FIRST),
                    new Turn("DD2-2", "더 쉬운 걸로 설명해 주세요", true,
                            MoveState.SLIGHTLY_EASIER, 9L, 9L, "두 점 사이의 거리", 14,
                            Verdict.CANNOT_MOVE, SRC_NONE + " — 선수 0개"))));

    /** 채점 대상 턴 수. 첫 턴(앵커 확정용)은 세지 않는다. */
    static long scoredTurnCount() {
        return ALL.stream().flatMap(s -> s.turns().stream()).filter(Turn::scored).count();
    }

    /** 음성 턴 수. 이 세트의 유일한 통제 장치이므로 따로 센다. */
    static long negativeTurnCount() {
        return ALL.stream()
                .flatMap(s -> s.turns().stream())
                .filter(t -> t.scored() && t.expectedState() == MoveState.NONE)
                .count();
    }
}
