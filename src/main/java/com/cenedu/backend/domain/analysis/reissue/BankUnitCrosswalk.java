package com.cenedu.backend.domain.analysis.reissue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 학습 단계 카탈로그의 개념을 뱅크 소단원에 잇는다.
 *
 * <p>두 데이터의 단위가 다르다. 카탈로그는 GCD와 LCM을 나누지만 뱅크는 한 소단원으로 묶고,
 * 개념명도 '소인수분해'와 '소인수 분해'로 띄어쓰기가 다르다. 문자열을 다듬어 맞히려 하면
 * 조용히 어긋나므로 사람이 정한 대응을 파일에 적어 둔다.
 *
 * <p>문제은행(curriculum_unit, source_topic_mapping)이 생기면 이 파일은 그 테이블로
 * 대체된다. 그때까지 임시로 두는 자리다.
 */
@Component
public class BankUnitCrosswalk {

    private static final String RESOURCE = "/reissue_bank_units.tsv";
    private final Map<String, String> byConceptId = new LinkedHashMap<>();

    public BankUnitCrosswalk() {
        try (InputStream input = BankUnitCrosswalk.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("소단원 대응표를 찾을 수 없습니다: " + RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String header = reader.readLine();
                if (header == null || !header.startsWith("concept_id")) {
                    throw new IllegalStateException("reissue_bank_units.tsv 헤더가 올바르지 않습니다.");
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] columns = line.split("\t", -1);
                    if (columns.length < 2 || columns[1].isBlank()) {
                        throw new IllegalStateException("소단원 대응이 비어 있습니다: " + line);
                    }
                    byConceptId.put(columns[0], columns[1]);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("reissue_bank_units.tsv 읽기 실패", e);
        }
    }

    /** 대응이 없으면 {@code null}이다. 비슷한 이름을 찾아 넘겨짚지 않는다. */
    public String bankUnit(String conceptId) {
        return byConceptId.get(conceptId);
    }
}
