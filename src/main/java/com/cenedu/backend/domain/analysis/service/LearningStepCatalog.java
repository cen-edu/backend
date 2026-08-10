package com.cenedu.backend.domain.analysis.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 실제로 사용하는 개념·단계 목록을 읽는다.
 *
 * <p>파일이 없거나 형식이 깨졌으면 기동을 막는다. 목록이 비어 있으면 모든 진단이 조용히
 * "알 수 없는 단계"로 떨어지는데, 그건 기동 실패보다 알아채기 어렵다.
 */
@Component
public class LearningStepCatalog {

    private static final String RESOURCE = "/learning_steps.tsv";
    private static final int COLUMN_COUNT = 6;

    private final Map<String, LearningStep> byKey = new LinkedHashMap<>();

    public LearningStepCatalog() {
        load();
    }

    public LearningStep requireApproved(String conceptId, String stepId) {
        LearningStep step = byKey.get(key(conceptId, stepId));
        if (step == null) {
            throw new IllegalArgumentException(
                    "사용 목록에 없는 개념·단계입니다: " + conceptId + ", " + stepId);
        }
        if (!step.approvedForDiagnosis()) {
            throw new IllegalArgumentException(
                    "아직 진단용으로 승인되지 않은 단계입니다: " + stepId);
        }
        return step;
    }

    private void load() {
        try (InputStream input = LearningStepCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("학습 단계 파일을 찾을 수 없습니다: " + RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String header = reader.readLine();
                if (header == null || !header.startsWith("concept_id")) {
                    throw new IllegalStateException("learning_steps.tsv 헤더가 올바르지 않습니다.");
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] c = line.split("\t", -1);
                    if (c.length != COLUMN_COUNT) {
                        throw new IllegalStateException("learning_steps.tsv 컬럼 수 오류: " + line);
                    }
                    LearningStep step = new LearningStep(
                            c[0], c[1], c[2], c[3], Boolean.parseBoolean(c[4]), c[5]);
                    String key = key(step.conceptId(), step.stepId());
                    if (byKey.putIfAbsent(key, step) != null) {
                        throw new IllegalStateException("중복 개념·단계: " + key);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("learning_steps.tsv 읽기 실패", e);
        }
    }

    private static String key(String conceptId, String stepId) {
        return conceptId + "::" + stepId;
    }

    public record LearningStep(
            String conceptId,
            String conceptName,
            String stepId,
            String stepName,
            boolean approvedForDiagnosis,
            String nextAction
    ) {
    }
}
