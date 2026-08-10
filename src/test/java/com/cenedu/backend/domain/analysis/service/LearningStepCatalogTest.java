package com.cenedu.backend.domain.analysis.service;

import com.cenedu.backend.domain.analysis.dto.LearningReportItem;
import com.cenedu.backend.domain.analysis.dto.LearningState;
import com.cenedu.backend.domain.analysis.entity.LearningStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 단계 목록이 실제로 함께 배포되는지 본다.
 *
 * <p>목록은 클래스패스 리소스라서 빌드 설정이 바뀌면 조용히 빠질 수 있다. 이 카탈로그는
 * {@code @Component} 라 빠지면 기동이 실패하는데, 그 실패를 배포 후에 발견하지 않으려고 둔다.
 */
@DisplayName("학습 단계 목록")
class LearningStepCatalogTest {

    private final LearningStepCatalog catalog = new LearningStepCatalog();

    @Test
    void approvedStepIsFound() {
        LearningStepCatalog.LearningStep step = catalog.requireApproved("GCD", "GCD_COMPUTE");

        assertThat(step.conceptName()).isEqualTo("최대공약수");
        assertThat(step.nextAction()).isNotBlank();
    }

    @Test
    void unknownStepIsRejected() {
        assertThatThrownBy(() -> catalog.requireApproved("GCD", "NOPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용 목록에 없는");
    }

    @Test
    @DisplayName("상태 코드는 프론트와 같은 값을 쓰고 한글은 표기로만 붙는다")
    void reportItemUsesSharedStatusCodes() {
        LearningStepCatalog.LearningStep step = catalog.requireApproved("GCD", "GCD_COMPUTE");
        LearningState state = new LearningState(
                "L-1", "GCD", "GCD_COMPUTE", LearningStatus.NEEDS_SUPPORT,
                3, 2, 0, 0, 0);

        LearningReportItem item = LearningReportItem.from(state, step);

        assertThat(item.status()).isEqualTo("priority");
        assertThat(item.statusName()).isEqualTo("집중 지도 필요");
        assertThat(item.errorCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("상태와 단계가 어긋나면 막는다")
    void mismatchedStepIsRejected() {
        LearningStepCatalog.LearningStep step = catalog.requireApproved("GCD", "GCD_COMPUTE");
        LearningState other = new LearningState(
                "L-1", "LCM", "LCM_COMPUTE", LearningStatus.CLEAR, 0, 0, 0, 0, 0);

        assertThatThrownBy(() -> LearningReportItem.from(other, step))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
