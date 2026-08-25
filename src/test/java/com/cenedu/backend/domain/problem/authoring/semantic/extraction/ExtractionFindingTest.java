package com.cenedu.backend.domain.problem.authoring.semantic.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 추출 실패 기록이 원인을 실제로 담고 있는지 고정한다.
 *
 * <p>예전에는 어느 단계에서 무엇이 잘못돼도 고정 문구 하나만 저장해서, 실패가 수천 건 쌓여도
 * 프롬프트를 고쳐야 할지 materializer를 고쳐야 할지 판단할 근거가 남지 않았다.
 */
class ExtractionFindingTest {

    @Test
    void 단계와_예외_종류와_메시지를_함께_남긴다() {
        String finding = ExtractionFinding.of("materialize",
                new IllegalStateException("unknown placeholder: RADIUS"));

        assertThat(finding)
                .contains("materialize")
                .contains("IllegalStateException")
                .contains("unknown placeholder: RADIUS");
    }

    /** 검증 위반 목록이나 model 본문이 통째로 들어와도 기록이 비대해지지 않아야 한다. */
    @Test
    void 지나치게_긴_메시지는_잘라서_남긴다() {
        String finding = ExtractionFinding.of("materialize",
                new IllegalArgumentException("가".repeat(2000)));

        assertThat(finding).hasSizeLessThan(600).endsWith("…");
    }

    @Test
    void 메시지가_없어도_단계와_예외_종류는_남는다() {
        String finding = ExtractionFinding.of("provider", new RuntimeException());

        assertThat(finding).contains("provider").contains("RuntimeException").contains("메시지 없음");
    }

    @Test
    void 줄바꿈을_한_줄로_정리한다() {
        String finding = ExtractionFinding.of("parse",
                new IllegalArgumentException("첫째 줄\n  둘째 줄"));

        assertThat(finding).contains("첫째 줄 둘째 줄").doesNotContain("\n");
    }
}
