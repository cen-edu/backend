package com.cenedu.backend.domain.problem.authoring.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import org.junit.jupiter.api.Test;

class VisualReferenceClassifierTest {
    private final VisualReferenceClassifier classifier = new VisualReferenceClassifier();

    @Test
    void 좌표평면과_정비례_근거가_있으면_좌표그래프로_분류한다() {
        assertThat(classifier.classify(QuestionPresentation.WITH_FIGURE, AssetRole.FIGURE,
                "좌표평면에서 정비례 관계의 그래프를 고르시오.", null))
                .isEqualTo(VisualReferenceKind.COORDINATE_GRAPH);
    }

    @Test
    void 일반_그래프_표현만으로는_좌표그래프로_분류하지_않는다() {
        assertThat(classifier.classify(QuestionPresentation.WITH_FIGURE, AssetRole.FIGURE,
                "도수분포다각형으로 나타낸 그래프를 보고 답하시오.", null))
                .isEqualTo(VisualReferenceKind.UNKNOWN_FIGURE);
    }

    @Test
    void 표_표현은_데이터표로_분류한다() {
        assertThat(classifier.classify(QuestionPresentation.WITH_TABLE, AssetRole.TABLE,
                "다음 표를 보고 답하시오.", null))
                .isEqualTo(VisualReferenceKind.DATA_TABLE);
    }
}
