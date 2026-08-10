package com.cenedu.backend.domain.analysis.reissue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReissueSelectorTest {

    private static final String UNIT = "최대공약수와 최소공배수";
    private static final ReissueSelector.ReissueTarget TARGET =
            new ReissueSelector.ReissueTarget(UNIT, QuestionDifficulty.MEDIUM);

    @Test
    void keepsOnlyTheSameUnitAndDifficulty() {
        var result = ReissueSelector.select(List.of(
                question("keep", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("other-unit", "소인수 분해", "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("other-difficulty", UNIT, "problemSolving", QuestionDifficulty.HIGH,
                        true, List.of("MODEL", "EXECUTE"))
        ), TARGET, Set.of(), "S1");

        assertEquals(List.of("keep"), ids(result));
        assertEquals(1, result.candidateCount());
    }

    /** 이미지 의존 문항은 텍스트로 낼 수 없어 뺀다. */
    @Test
    void dropsImageQuestions() {
        var result = ReissueSelector.select(List.of(
                question("image", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        false, List.of("MODEL", "EXECUTE")),
                question("fine", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE"))
        ), TARGET, Set.of(), "S1");

        assertEquals(List.of("fine"), ids(result));
    }

    /**
     * 문항 분류는 하나도 쓰지 않는다. 평가 영역이 달라도, 구간 순서가 뒤로 가도 순위가
     * 바뀌지 않는다. 남는 기준은 빈칸 수뿐이다.
     */
    @Test
    void doesNotUseAnyQuestionClassification() {
        var result = ReissueSelector.select(List.of(
                // 평가 영역이 다르고 구간 순서도 역행하지만 빈칸이 적어 먼저 나온다.
                question("other-area-few", UNIT, "calculation", QuestionDifficulty.MEDIUM,
                        true, List.of("EXECUTE", "MODEL")),
                question("same-area-many", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE", "ANSWER", "INTERPRET"))
        ), TARGET, Set.of(), "S1", 2);

        assertEquals(List.of("other-area-few", "same-area-many"), ids(result));
    }

    @Test
    void doesNotServeTheSameQuestionTwice() {
        var result = ReissueSelector.select(List.of(
                question("served", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("fresh", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE"))
        ), TARGET, Set.of("served"), "S1");

        assertEquals(List.of("fresh"), ids(result));
    }

    /** 빈칸이 적은 문항이 먼저다. 학생이 풀 것도 해석할 것도 적다. */
    @Test
    void prefersTheFewerBlanks() {
        var result = ReissueSelector.select(List.of(
                question("three", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE", "ANSWER")),
                question("two", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("one", UNIT, "calculation", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL"))
        ), TARGET, Set.of(), "S1", 3);

        assertEquals(List.of("one", "two", "three"), ids(result));
    }

    @Test
    void differentStudentsGetDifferentSetsWhenCandidatesAreTied() {
        List<BankQuestion> bank = List.of(
                question("q1", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("q2", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("q3", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("q4", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("q5", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("q6", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")));

        var first = ids(ReissueSelector.select(bank, TARGET, Set.of(), "S1"));
        var second = ids(ReissueSelector.select(bank, TARGET, Set.of(), "S2"));

        assertEquals(3, first.size());
        assertNotEquals(first, second);
    }

    /** 새로고침마다 문항이 바뀌면 교사가 무엇을 냈는지 확인할 수 없다. */
    @Test
    void theSameStudentAlwaysGetsTheSameSet() {
        List<BankQuestion> bank = List.of(
                question("q1", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("q2", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("q3", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")),
                question("q4", UNIT, "problemSolving", QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE")));

        assertEquals(ids(ReissueSelector.select(bank, TARGET, Set.of(), "S1")),
                ids(ReissueSelector.select(bank, TARGET, Set.of(), "S1")));
    }

    /** 평가 영역이 비어 있는 문항도 후보에 남는다. 선정에 쓰지 않는 값이기 때문이다. */
    @Test
    void keepsQuestionsWithoutAnEvaluationArea() {
        var result = ReissueSelector.select(List.of(
                question("no-area", UNIT, null, QuestionDifficulty.MEDIUM,
                        true, List.of("MODEL", "EXECUTE"))
        ), TARGET, Set.of(), "S1");

        assertTrue(result.questions().size() == 1);
    }

    private static List<String> ids(ReissueSelector.Result result) {
        return result.questions().stream().map(BankQuestion::id).toList();
    }

    private static BankQuestion question(
            String id, String unit, String area, QuestionDifficulty difficulty,
            boolean imageFree, List<String> stages) {
        return new BankQuestion(id, unit, area, difficulty, imageFree, stages, id + " 본문");
    }
}
