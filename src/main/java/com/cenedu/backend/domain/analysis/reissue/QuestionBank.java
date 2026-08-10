package com.cenedu.backend.domain.analysis.reissue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 재출제 후보 문항을 메모리에 올린다.
 *
 * <p>원본 두 파일을 읽는다. 문항 구조는 단계형 뱅크(jsonl)에, 평가 영역은 조인표(tsv)에 있다.
 * 조인표는 원본 {@code question_sector1}에서 뽑은 값이라 LLM을 부르지 않는다.
 *
 * <p><b>임시 구현이다.</b> 정본은 problem 도메인의 문제은행 테이블이지만 그 도메인이 아직
 * 비어 있다. AGENTS.md 3절이 다른 도메인의 리포지토리를 직접 참조하지 못하게 하므로, 문제은행이
 * 생기면 조회 방식을 그 도메인과 협의해 바꾼다.
 *
 * <p>파일은 저장소가 추적하지 않아 없을 수 있다. 없으면 서버 기동을 막지 않고 비어 있는 채로
 * 둔 뒤, 재출제를 요청할 때 이유를 알린다. 보고서·대시보드는 뱅크 없이도 동작해야 한다.
 */
@Component
public class QuestionBank {

    private static final Logger log = LoggerFactory.getLogger(QuestionBank.class);

    private final List<BankQuestion> questions;
    private final Path bankPath;

    /**
     * {@code ObjectMapper} 를 주입받지 않고 자기 것을 만든다. 이 컨텍스트에는 공용
     * {@code ObjectMapper} 빈이 없고, 빈을 만드는 자리({@code global/config})는 다른 담당자
     * 소유라 여기서 손대지 않는다(AGENTS.md 2절).
     *
     * <p>읽는 것은 서비스 밖 원본 파일이라 앱의 직렬화 설정과 맞출 이유도 없다.
     */
    public QuestionBank(
            @Value("${reissue.bank-path:data/aihub/AIHub_math_middle1"
                    + "/110_auto_solution_middle1/derived/step-response"
                    + "/step_fill_questions.jsonl}") String bankPath,
            @Value("${reissue.area-table-path:data/aihub/AIHub_math_middle1"
                    + "/110_auto_solution_middle1/derived/step-response"
                    + "/question_evaluation_area.tsv}") String areaPath
    ) {
        this.bankPath = Path.of(bankPath);
        this.questions = load(new ObjectMapper(), this.bankPath, Path.of(areaPath));
    }

    public List<BankQuestion> questions() {
        return questions;
    }

    public boolean isEmpty() {
        return questions.isEmpty();
    }

    /** 뱅크가 비어 있을 때 무엇을 놓았는지 알려 준다. */
    public String missingMessage() {
        return "재출제 문항 뱅크를 찾을 수 없습니다: " + bankPath.toAbsolutePath();
    }

    private static List<BankQuestion> load(ObjectMapper json, Path bankPath, Path areaPath) {
        if (!Files.exists(bankPath)) {
            log.warn("재출제 뱅크 파일이 없어 비어 있는 상태로 시작합니다: {}", bankPath.toAbsolutePath());
            return List.of();
        }
        Map<String, String> areas = loadAreas(areaPath);
        List<BankQuestion> loaded = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(bankPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                BankQuestion question = toQuestion(json.readTree(line), areas);
                if (question != null) {
                    loaded.add(question);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("재출제 뱅크를 읽을 수 없습니다: " + bankPath, e);
        }
        log.info("재출제 뱅크 {}문항을 읽었습니다. 평가 영역 {}건.", loaded.size(), areas.size());
        return List.copyOf(loaded);
    }

    private static BankQuestion toQuestion(JsonNode row, Map<String, String> areas) {
        String reference = row.path("generationMetadata").path("sourceQuestionRef").asText("");
        String id = reference.contains(":")
                ? reference.substring(reference.lastIndexOf(':') + 1) : reference;
        if (id.isBlank()) {
            return null;
        }

        String unit = null;
        for (JsonNode mapping : row.path("curriculumMappings")) {
            if (mapping.path("isPrimary").asBoolean()) {
                String key = mapping.path("curriculumExternalKey").asText("");
                unit = key.contains(">") ? key.substring(key.lastIndexOf('>') + 1) : key;
            }
        }
        if (unit == null || unit.isBlank()) {
            return null;
        }

        // 최상위 difficulty 는 상·중을 한 칸으로 접은 파생값이라 쓰지 않는다.
        JsonNode label = row.path("sourceMetadata").path("questionDifficulty");
        QuestionDifficulty difficulty = label.isMissingNode()
                ? null : QuestionDifficulty.fromSourceLabel(label.asText());
        if (difficulty == null) {
            return null;
        }

        List<String> stages = new ArrayList<>();
        for (JsonNode blank : row.path("answerSpec").path("blanks")) {
            stages.add(blank.path("diagnosticType").asText(null));
        }

        return new BankQuestion(
                id, unit, areas.get(id), difficulty,
                "NONE".equals(row.path("visualDependency").asText()),
                stages, row.path("promptText").asText(""));
    }

    private static Map<String, String> loadAreas(Path areaPath) {
        if (!Files.exists(areaPath)) {
            log.warn("평가 영역 조인표가 없어 영역 없이 시작합니다: {}", areaPath.toAbsolutePath());
            return Map.of();
        }
        Map<String, String> areas = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(areaPath, StandardCharsets.UTF_8);
            for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
                String[] columns = line.split("\t", -1);
                // 원본에 영역이 없는 문항은 빈 칸으로 들어온다. 채우지 않는다.
                if (columns.length >= 2 && !columns[1].isBlank()) {
                    areas.put(columns[0], columns[1]);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("평가 영역 조인표를 읽을 수 없습니다: " + areaPath, e);
        }
        return Map.copyOf(areas);
    }
}
