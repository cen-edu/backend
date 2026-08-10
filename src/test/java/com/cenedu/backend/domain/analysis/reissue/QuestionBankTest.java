package com.cenedu.backend.domain.analysis.reissue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionBankTest {

    
    private static final String ROW = """
            {"generationMetadata":{"sourceQuestionRef":"110:5079_1230"},\
            "curriculumMappings":[{"isPrimary":true,\
            "curriculumExternalKey":"EBS:M1:SUB:수와 연산>소인수 분해>최대공약수와 최소공배수"}],\
            "sourceMetadata":{"questionDifficulty":2},"difficulty":1,\
            "visualDependency":"NONE","promptText":"두 자연수의 최대공약수를 구하여라.",\
            "answerSpec":{"blanks":[{"diagnosticType":"MODEL"},{"diagnosticType":"EXECUTE"}]}}""";

    @Test
    void readsUnitDifficultyAndAreaFromTheOriginalLabels(@TempDir Path dir) throws IOException {
        QuestionBank bank = bank(dir, ROW, "question_id\tevaluation_area\tevaluation_area_name\n"
                + "5079_1230\tproblemSolving\t문제해결\n");

        assertEquals(1, bank.questions().size());
        BankQuestion question = bank.questions().get(0);
        assertEquals("5079_1230", question.id());
        assertEquals("최대공약수와 최소공배수", question.unitName());
        assertEquals("problemSolving", question.evaluationArea());
        // 최상위 difficulty가 1이어도 원본 라벨 2를 읽어 '중'이어야 한다.
        assertEquals(QuestionDifficulty.MEDIUM, question.difficulty());
        assertTrue(question.imageFree());
        assertEquals(0, question.stagePosition("MODEL"));
    }

    /** 조인표에 없는 문항은 영역을 비운다. 없는 값을 지어내지 않는다. */
    @Test
    void leavesTheAreaEmptyWhenTheJoinTableHasNoRow(@TempDir Path dir) throws IOException {
        QuestionBank bank = bank(dir, ROW, "question_id\tevaluation_area\tevaluation_area_name\n");

        assertNull(bank.questions().get(0).evaluationArea());
    }

    /** 뱅크 파일이 없어도 서버는 떠야 한다. 보고서·대시보드는 뱅크와 무관하다. */
    @Test
    void startsEmptyWhenTheBankFileIsMissing(@TempDir Path dir) {
        QuestionBank bank = new QuestionBank(dir.resolve("none.jsonl").toString(), dir.resolve("none.tsv").toString());

        assertTrue(bank.isEmpty());
        assertTrue(bank.missingMessage().contains("none.jsonl"));
    }

    private static QuestionBank bank(Path dir, String rows, String areas) throws IOException {
        Path bankPath = dir.resolve("bank.jsonl");
        Path areaPath = dir.resolve("areas.tsv");
        Files.writeString(bankPath, rows + "\n", StandardCharsets.UTF_8);
        Files.writeString(areaPath, areas, StandardCharsets.UTF_8);
        return new QuestionBank(bankPath.toString(), areaPath.toString());
    }
}
