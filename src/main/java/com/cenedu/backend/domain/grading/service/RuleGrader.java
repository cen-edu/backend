package com.cenedu.backend.domain.grading.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cenedu.backend.global.common.enums.CompareMethod;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 규칙 채점 5종. 결정론적 비교뿐이며 LLM 을 부르지 않는다.
 *
 * <p><b>{@code RUBRIC} 을 다루지 않는 것은 미구현이 아니라 의도다.</b> 서술형 채점은 구현돼 있고
 * ({@code EssayGradingService} → {@code EssayGradingPort}), {@code AnswerGradingService.gradeOne}
 * 이 트랜잭션을 열기 전에 그쪽으로 갈라 보낸다. 이 클래스가 결정론 전용으로 남아야 규칙 채점의
 * 판정이 모델 사정에 흔들리지 않고, 검증 어댑터도 그 전제로 이 클래스를 읽는다.
 * 아래 {@code RUBRIC} 분기는 호출부가 잘못 불렀을 때를 위한 방어다.
 *
 * <p>판정은 <b>정답 / 오답 / 판정 불가</b> 셋이다. 못 읽은 답을 오답으로 접으면 채점기 결함이 학생
 * 점수로 조용히 흘러가므로, 읽지 못한 것은 {@link Verdict#failure}로 남겨 호출부가 {@code FAILED}로
 * 기록하게 한다.
 */
@Component
@RequiredArgsConstructor
public class RuleGrader {

    /** 표본 대입에 쓰는 값. 0·1·부호 대칭을 피해 우연한 일치를 줄인다. */
    private static final double[] SAMPLES = {2.0, 3.0, 5.0, 7.0, 11.0};

    /** 부동소수 비교 허용 오차. 정답이 클수록 상대 오차로 판단한다. */
    private static final double EPSILON = 1e-9;

    private final ExpressionEvaluator evaluator;

    /**
     * 채점 결과.
     *
     * @param failureReason {@code null}이 아니면 판정 자체를 못 한 것이다. {@code correct}는 의미 없다
     */
    public record Verdict(boolean correct, String failureReason) {

        static Verdict of(boolean correct) {
            return new Verdict(correct, null);
        }

        static Verdict failure(String reason) {
            return new Verdict(false, reason);
        }

        public boolean isFailure() {
            return failureReason != null;
        }
    }

    /**
     * 칸 하나를 채점한다.
     *
     * @param compareMethod {@code submission_answer}에 저장된 <b>스냅샷</b>을 넘긴다.
     *                      {@code problem_answer_unit}에서 다시 읽으면, 출제 후 방법이 바뀌었을 때
     *                      과거 채점 결과가 새 방법 쪽으로 집계돼 오답률 측정이 무너진다(명세 7절)
     * @param studentAnswer 정규형으로 바꾼 학생 답. 정규화에 실패했으면 {@code null}
     * @param correctAnswer 정규형으로 바꾼 정답
     */
    public Verdict grade(CompareMethod compareMethod, String studentAnswer, String correctAnswer) {
        if (compareMethod == CompareMethod.RUBRIC) {
            return Verdict.failure("서술형은 규칙 채점 대상이 아니다");
        }
        if (studentAnswer == null) {
            return Verdict.failure("학생 답을 정규화하지 못함");
        }
        if (correctAnswer == null) {
            return Verdict.failure("정답이 등록되지 않음");
        }
        return switch (compareMethod) {
            case EXACT -> Verdict.of(studentAnswer.equals(correctAnswer));
            case SET -> gradeSet(studentAnswer, correctAnswer);
            case VALUE -> gradeValue(studentAnswer, correctAnswer);
            case SUBST -> gradeSubstitution(studentAnswer, correctAnswer);
            // CHOICE 는 보기 ID 로 판정한다(gradeChoice). 여기 오면 호출부가 잘못 부른 것이다.
            case CHOICE -> Verdict.failure("객관식은 보기 ID로 채점한다");
            case RUBRIC -> Verdict.failure("서술형은 규칙 채점 대상이 아니다");
        };
    }

    /**
     * 객관식. 문자열이 아니라 <b>보기 ID</b>로 비교한다 — 보기 본문이 같은 글자여도 다른 보기일 수 있고,
     * 정답 컬럼은 1-based 표시 순번이라 본문과 축이 다르다.
     */
    public Verdict gradeChoice(Long selectedChoiceId, Long correctChoiceId) {
        if (correctChoiceId == null) {
            return Verdict.failure("정답 보기를 찾을 수 없음");
        }
        if (selectedChoiceId == null) {
            return Verdict.of(false);
        }
        return Verdict.of(selectedChoiceId.equals(correctChoiceId));
    }

    /**
     * 쉼표로 끊어 집합으로 본다. 순서와 중복은 무시한다.
     *
     * <p>원소가 <b>전부 수식으로 읽히면 값으로 짝짓는다</b> — {@code 2x^2/5}와 {@code 0.4x^2}는
     * 글자가 달라도 같은 답이다. 하나라도 못 읽으면(한글 답 {@code ㄱ,ㄷ,ㅁ} 등) 문자열 비교로
     * 물러선다. 새 라이브러리를 들이지 않고 {@code SUBST}가 쓰는 표본 대입을 그대로 재사용한다.
     */
    private Verdict gradeSet(String studentAnswer, String correctAnswer) {
        Set<String> student = splitToSet(studentAnswer);
        Set<String> correct = splitToSet(correctAnswer);
        if (correct.isEmpty()) {
            return Verdict.failure("정답 집합이 비어 있음");
        }
        if (student.equals(correct)) {
            return Verdict.of(true);
        }
        if (student.size() != correct.size()) {
            return Verdict.of(false);
        }
        return matchByValue(student, correct).orElseGet(() -> Verdict.of(false));
    }

    /**
     * 원소를 값으로 일대일 짝짓는다. 못 읽는 원소가 하나라도 있으면 {@code Optional.empty()} —
     * 호출부가 문자열 비교 결과(오답)를 그대로 쓴다.
     */
    private Optional<Verdict> matchByValue(Set<String> student, Set<String> correct) {
        List<String> remaining = new ArrayList<>(correct);
        for (String element : student) {
            int matched = -1;
            for (int i = 0; i < remaining.size(); i++) {
                Verdict verdict = gradeSubstitution(element, remaining.get(i));
                if (verdict.isFailure()) {
                    return Optional.empty();
                }
                if (verdict.correct()) {
                    matched = i;
                    break;
                }
            }
            if (matched < 0) {
                return Optional.of(Verdict.of(false));
            }
            remaining.remove(matched);
        }
        return Optional.of(Verdict.of(remaining.isEmpty()));
    }

    private Set<String> splitToSet(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    /** 수치 동치. 분수·소수를 섞어 써도 값이 같으면 정답이다({@code 40/3} = {@code 13.333…}은 아니다). */
    private Verdict gradeValue(String studentAnswer, String correctAnswer) {
        try {
            double correct = evaluator.evaluate(correctAnswer, Map.of());
            double student = evaluator.evaluate(studentAnswer, Map.of());
            return Verdict.of(closeEnough(student, correct));
        } catch (ExpressionEvaluator.ParseException e) {
            return Verdict.failure("수식을 읽지 못함: " + e.getMessage());
        }
    }

    /**
     * 식 동치. 변수에 표본값을 넣어 두 식이 같은 값을 내는지 본다.
     *
     * <p>등식({@code a=b})은 양변을 한쪽으로 몰아 {@code a-b} 형태로 만든 뒤 비교하고, 비({@code a:b})는
     * {@code a/b}로 바꾼다 — {@code 16:32=54:x}처럼 비례식이면 양쪽 다 거쳐 하나의 식이 된다.
     *
     * <p>학생 식에만 있는 변수가 있으면 판정하지 않는다. 정답에 없는 문자를 임의값으로 채우면
     * 아무 식이나 우연히 맞을 수 있다.
     */
    private Verdict gradeSubstitution(String studentAnswer, String correctAnswer) {
        // 부등식은 값이 아니라 참·거짓이다. 양변을 빼는 등식 처리를 태우면 '>=' 의 '=' 가 등호로
        // 잘려 식이 망가지므로, 비교식은 원문 그대로 판정한다.
        boolean inequality = hasInequality(studentAnswer) || hasInequality(correctAnswer);
        String correctForm = inequality ? correctAnswer : toComparableForm(correctAnswer);
        String studentForm = inequality ? studentAnswer : toComparableForm(studentAnswer);
        try {
            Set<String> correctVariables = evaluator.findVariables(correctForm);
            Set<String> studentVariables = evaluator.findVariables(studentForm);
            if (!correctVariables.containsAll(studentVariables)) {
                return Verdict.failure("정답에 없는 변수가 답에 있음");
            }
            if (correctVariables.isEmpty()) {
                return Verdict.of(compare(inequality, studentForm, correctForm, Map.of()));
            }
            for (double sample : SAMPLES) {
                Map<String, Double> values = new LinkedHashMap<>();
                double offset = 0;
                for (String variable : correctVariables) {
                    values.put(variable, sample + offset);
                    offset += 1;
                }
                if (!compare(inequality, studentForm, correctForm, values)) {
                    return Verdict.of(false);
                }
            }
            return Verdict.of(true);
        } catch (ExpressionEvaluator.ParseException e) {
            return Verdict.failure("수식을 읽지 못함: " + e.getMessage());
        }
    }

    /**
     * 한 표본에서 두 답이 같은지. 부등식이면 <b>진리값</b>을, 아니면 <b>값</b>을 견준다.
     */
    private boolean compare(boolean inequality, String studentForm, String correctForm,
                            Map<String, Double> values) {
        if (inequality) {
            return evaluator.evaluateComparison(studentForm, values)
                    == evaluator.evaluateComparison(correctForm, values);
        }
        return closeEnough(evaluator.evaluate(studentForm, values),
                evaluator.evaluate(correctForm, values));
    }

    /** 부등호가 있는가. {@code >=} {@code <=} 도 여기 걸린다. */
    private boolean hasInequality(String expression) {
        return expression.indexOf('<') >= 0 || expression.indexOf('>') >= 0;
    }

    /** {@code a=b} → {@code (a)-(b)}, {@code a:b} → {@code (a)/(b)}. 등식이 먼저다. */
    private String toComparableForm(String expression) {
        int equals = expression.indexOf('=');
        if (equals >= 0) {
            return "(" + ratioToDivision(expression.substring(0, equals)) + ")-("
                    + ratioToDivision(expression.substring(equals + 1)) + ")";
        }
        return ratioToDivision(expression);
    }

    private String ratioToDivision(String expression) {
        int colon = expression.indexOf(':');
        if (colon < 0) {
            return expression;
        }
        return "(" + expression.substring(0, colon) + ")/(" + expression.substring(colon + 1) + ")";
    }

    /**
     * 두 값이 같은가. 값이 커질수록 부동소수 오차도 커지므로 상대 오차를 함께 본다.
     *
     * <p>등식을 한쪽으로 몬 식은 정답일 때 0이 되므로 절대 오차가 필요하다.
     */
    private boolean closeEnough(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }
        double difference = Math.abs(a - b);
        if (difference <= EPSILON) {
            return true;
        }
        return difference <= EPSILON * Math.max(Math.abs(a), Math.abs(b));
    }
}
