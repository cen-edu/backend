package com.cenedu.backend.domain.grading.service;

import java.util.ArrayList;
import java.util.List;

import com.cenedu.backend.global.common.enums.CompareMethod;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙 채점 5종의 판정 전수표(명세 7절 · task_06 §7-6).
 *
 * <p>케이스는 실제 문제은행 데이터 모양에서 뽑았다 — 정답이 {@code \frac{40}{3}}/{@code 40/3} 두 축으로
 * 저장돼 있고({@code answer_raw}/{@code answer_normalized}), {@code SUBST}에는 {@code 8/9*pi*x} 같은
 * 식과 {@code 16:32=54:x} 같은 비례식이 있으며, {@code SET}은 쉼표로 끊긴 목록이다.
 *
 * <p>기대값은 <b>정답/오답/판정불가 3값</b>이다. 판정불가를 오답과 한 칸에 넣으면 채점기 결함이
 * 학생 점수로 새는 것을 이 테스트가 못 잡는다.
 */
class RuleGraderTest {

    private final AnswerNormalizer normalizer = new AnswerNormalizer();
    private final RuleGrader grader = new RuleGrader(new ExpressionEvaluator());

    private enum Expected { CORRECT, WRONG, FAILED }

    private record Case(CompareMethod method, String bucket, String studentRaw, String correctRaw,
                        Expected expected) {
    }

    @Test
    @DisplayName("compare_method 5종의 정답·오답·경계 판정이 기대와 같다")
    void gradesEveryCompareMethod() {
        List<Case> cases = new ArrayList<>();

        // ===== VALUE — 수치 동치 =====
        cases.add(new Case(CompareMethod.VALUE, "정답", "7", "7", Expected.CORRECT));
        cases.add(new Case(CompareMethod.VALUE, "정답", "\\frac{40}{3}", "40/3", Expected.CORRECT));
        cases.add(new Case(CompareMethod.VALUE, "정답", "0.16", "0.16", Expected.CORRECT));
        cases.add(new Case(CompareMethod.VALUE, "오답", "8", "7", Expected.WRONG));
        cases.add(new Case(CompareMethod.VALUE, "오답", "13.33", "40/3", Expected.WRONG));
        cases.add(new Case(CompareMethod.VALUE, "오답", "-3/2", "3/2", Expected.WRONG));
        // 경계: 필기가 내는 \dfrac, 데이터에 실재하는 선행 +, 분수와 소수 혼용, 읽을 수 없는 답
        cases.add(new Case(CompareMethod.VALUE, "경계", "\\dfrac{9}{2}", "9/2", Expected.CORRECT));
        cases.add(new Case(CompareMethod.VALUE, "경계", "2.5", "5/2", Expected.CORRECT));
        cases.add(new Case(CompareMethod.VALUE, "경계", "+2", "2", Expected.CORRECT));
        cases.add(new Case(CompareMethod.VALUE, "경계", "몰라요", "7", Expected.FAILED));

        // ===== EXACT — 정규화 후 문자열 일치 =====
        cases.add(new Case(CompareMethod.EXACT, "정답", "정칠각형", "정칠각형", Expected.CORRECT));
        cases.add(new Case(CompareMethod.EXACT, "정답", "x", "x", Expected.CORRECT));
        cases.add(new Case(CompareMethod.EXACT, "정답", "같다", "같다", Expected.CORRECT));
        cases.add(new Case(CompareMethod.EXACT, "오답", "정육각형", "정칠각형", Expected.WRONG));
        cases.add(new Case(CompareMethod.EXACT, "오답", "y", "x", Expected.WRONG));
        cases.add(new Case(CompareMethod.EXACT, "오답", "다르다", "같다", Expected.WRONG));
        // 경계: 공백은 지운다 / 대소문자는 다른 답이다(x 와 X 는 다른 변수) / 빈 답
        cases.add(new Case(CompareMethod.EXACT, "경계", "정 칠 각 형", "정칠각형", Expected.CORRECT));
        cases.add(new Case(CompareMethod.EXACT, "경계", "X", "x", Expected.WRONG));
        cases.add(new Case(CompareMethod.EXACT, "경계", "   ", "x", Expected.FAILED));

        // ===== SET — 쉼표로 끊어 집합 비교 =====
        cases.add(new Case(CompareMethod.SET, "정답", "13,29,37", "13, 29, 37", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SET, "정답", "ㄱ,ㄷ,ㅁ", "ㄱ,ㄷ,ㅁ", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SET, "정답", "1:1", "1:1", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SET, "오답", "13,29", "13,29,37", Expected.WRONG));
        cases.add(new Case(CompareMethod.SET, "오답", "13,29,37,41", "13,29,37", Expected.WRONG));
        cases.add(new Case(CompareMethod.SET, "오답", "ㄱ,ㄴ", "ㄱ,ㄷ", Expected.WRONG));
        // 경계: 순서 무시 / 중복 무시 / 콜론 주변 공백(데이터에 '8 : 15' 실재)
        cases.add(new Case(CompareMethod.SET, "경계", "37,13,29", "13,29,37", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SET, "경계", "13,29,37,37", "13,29,37", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SET, "경계", "8 : 15", "8:15", Expected.CORRECT));

        // ===== SUBST — 표본 대입 후 동치 =====
        cases.add(new Case(CompareMethod.SUBST, "정답", "2x", "2*x", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SUBST, "정답", "\\frac{8}{9}\\pi x", "8/9*pi*x", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SUBST, "정답", "10\\pi", "10*pi", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SUBST, "오답", "3*x", "2*x", Expected.WRONG));
        cases.add(new Case(CompareMethod.SUBST, "오답", "x", "2*x", Expected.WRONG));
        cases.add(new Case(CompareMethod.SUBST, "오답", "9/8*pi*x", "8/9*pi*x", Expected.WRONG));
        // 경계: 전개형도 동치 / 등식은 한쪽으로 몰아 비교 / 비례식 / 정답에 없는 변수 / 못 읽는 식
        cases.add(new Case(CompareMethod.SUBST, "경계", "2*(x+1)", "2*x+2", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SUBST, "경계", "8/9*pi*x=6*pi", "8/9*pi*x=6*pi", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SUBST, "경계", "16:32=54:x", "16:32=54:x", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SUBST, "경계", "2*y", "2*x", Expected.FAILED));

        // ===== 적재가 남긴 LaTeX 잔재 — 정답 쪽에 실재하는 모양이다(실측 228건) =====
        // ~ 는 범위가 아니라 비분리 공백이다.
        cases.add(new Case(CompareMethod.SUBST, "잔재", "10*pi", "10 \\pi ~", Expected.CORRECT));
        cases.add(new Case(CompareMethod.VALUE, "잔재", "60/13", "\\frac{60}{13} ~", Expected.CORRECT));
        // 짝 없는 \end{array} — 추출이 표의 마지막 칸만 잘라 온 흔적.
        cases.add(new Case(CompareMethod.VALUE, "잔재", "324", "324\\end{array}", Expected.CORRECT));
        // 각도. 값 자체는 숫자다.
        cases.add(new Case(CompareMethod.VALUE, "잔재", "35", "35^{\\circ}", Expected.CORRECT));
        // 중괄호 지수 — 소인수분해 답이 전부 이 모양이다.
        cases.add(new Case(CompareMethod.SUBST, "잔재", "2^2*13", "2^{2} \\times 13", Expected.CORRECT));
        // 보기 번호 접두는 답이 아니다.
        cases.add(new Case(CompareMethod.SUBST, "잔재", "3^3", "① 3^{3}", Expected.CORRECT));
        // 답 전체를 감싼 꺾쇠. 부등호가 아니다.
        cases.add(new Case(CompareMethod.SUBST, "잔재", "14/5*a", "<14/5 a>", Expected.CORRECT));
        // 표 구분자 &.
        cases.add(new Case(CompareMethod.VALUE, "잔재", "105", "105^{\\circ} &", Expected.CORRECT));
        // 선분 기호와 한글 라벨은 기호만 남긴다.
        cases.add(new Case(CompareMethod.EXACT, "잔재", "AD", "\\overline{AD}", Expected.CORRECT));
        cases.add(new Case(CompareMethod.EXACT, "잔재", "C", "점 C", Expected.CORRECT));

        // ===== 절댓값·부등식 — 파서 확장 =====
        cases.add(new Case(CompareMethod.SUBST, "부등식", "2|b|", "2*|b|", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SUBST, "부등식", "-a+13>=1", "-a+13\\ge1", Expected.CORRECT));
        // ⚠️ 한계를 현실로 고정한다: 표본 대입은 > 와 >= 를 가르지 못한다. 둘은 경계점(a=12)
        // 하나에서만 갈리는데 표본이 그 점에 닿지 않는다. 엄격/비엄격을 바꿔 쓴 답이 정답으로
        // 읽히며, 이 성질이 바뀌면 이 케이스가 먼저 깨진다.
        cases.add(new Case(CompareMethod.SUBST, "부등식", "-a+13>1", "-a+13\\ge1", Expected.CORRECT));
        // 부등호 방향이 다르면 표본에서 갈린다.
        cases.add(new Case(CompareMethod.SUBST, "부등식", "-a+13<1", "-a+13\\ge1", Expected.WRONG));
        // 연쇄 부등식은 이웃한 쌍을 모두 만족해야 참이다.
        cases.add(new Case(CompareMethod.SUBST, "부등식", "b<c<a", "b<c<a", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SUBST, "부등식", "a<c<b", "b<c<a", Expected.WRONG));

        // ===== SET 의 수학적 동치 =====
        // 글자는 달라도 값이 같으면 같은 원소다.
        cases.add(new Case(CompareMethod.SET, "동치", "0.4*x^2,-5*x^2",
                "-5x^{2}, \\frac{2 x^{2}}{5}", Expected.CORRECT));
        cases.add(new Case(CompareMethod.SET, "동치", "0.5*x^2,-5*x^2",
                "-5x^{2}, \\frac{2 x^{2}}{5}", Expected.WRONG));

        // ===== RUBRIC — 이번 범위에서는 판정하지 않는다(명세 7절) =====
        cases.add(new Case(CompareMethod.RUBRIC, "경계", "학생 서술 답안", null, Expected.FAILED));

        List<String> mismatches = new ArrayList<>();
        System.out.println("| compare_method | 구분 | 학생 답 | 정답 | 기대 | 실제 |");
        System.out.println("|---|---|---|---|---|---|");
        for (Case testCase : cases) {
            Expected actual = judge(testCase);
            System.out.printf("| %s | %s | `%s` | `%s` | %s | %s |%n",
                    testCase.method(), testCase.bucket(), testCase.studentRaw(),
                    testCase.correctRaw(), testCase.expected(), actual);
            if (actual != testCase.expected()) {
                mismatches.add("%s %s: %s vs %s → 기대 %s, 실제 %s".formatted(
                        testCase.method(), testCase.bucket(), testCase.studentRaw(),
                        testCase.correctRaw(), testCase.expected(), actual));
            }
        }
        assertThat(mismatches).isEmpty();
    }

    @Test
    @DisplayName("객관식은 보기 ID로 판정하고, 정답 보기를 못 찾으면 판정하지 않는다")
    void gradesChoiceByChoiceId() {
        assertThat(grader.gradeChoice(42L, 42L).correct()).isTrue();
        assertThat(grader.gradeChoice(41L, 42L).correct()).isFalse();
        // 미응답은 오답이다 — 학생이 고르지 않았다는 사실 자체가 판정 가능하다.
        assertThat(grader.gradeChoice(null, 42L)).satisfies(verdict -> {
            assertThat(verdict.isFailure()).isFalse();
            assertThat(verdict.correct()).isFalse();
        });
        // 정답 보기를 못 찾은 것은 데이터 문제라 판정 불가다.
        assertThat(grader.gradeChoice(42L, null).isFailure()).isTrue();
    }

    @Test
    @DisplayName("정규화는 멱등이다 — 이미 정규형인 정답을 다시 넣어도 그대로다")
    void normalizationIsIdempotent() {
        List<String> alreadyNormalized = List.of(
                "40/3", "8/9*pi*x", "8/9*pi*x=6*pi", "16:32=54:x", "13,29,37",
                "점이실선으로이어져있다.", "정칠각형", "0.16", "-3/2", "10*pi");
        for (String value : alreadyNormalized) {
            String once = normalizer.normalize(value, null);
            assertThat(once).as("1회 정규화: %s", value).isEqualTo(value);
            assertThat(normalizer.normalize(once, null)).as("2회 정규화: %s", value).isEqualTo(value);
        }
    }

    @Test
    @DisplayName("표시용 단위는 떼어 내고 비교한다")
    void stripsDisplayUnit() {
        assertThat(normalizer.normalize("26cm^2", "cm^2")).isEqualTo("26");
        assertThat(normalizer.normalize("26", "cm^2")).isEqualTo("26");
    }

    private Expected judge(Case testCase) {
        if (testCase.method() == CompareMethod.RUBRIC) {
            return toExpected(grader.grade(testCase.method(), "무엇이든", "무엇이든"));
        }
        String student = normalizer.normalize(testCase.studentRaw(), null);
        String correct = normalizer.normalize(testCase.correctRaw(), null);
        return toExpected(grader.grade(testCase.method(), student, correct));
    }

    private Expected toExpected(RuleGrader.Verdict verdict) {
        if (verdict.isFailure()) {
            return Expected.FAILED;
        }
        return verdict.correct() ? Expected.CORRECT : Expected.WRONG;
    }
}
