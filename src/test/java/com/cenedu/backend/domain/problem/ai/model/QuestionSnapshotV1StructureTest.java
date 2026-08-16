package com.cenedu.backend.domain.problem.ai.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionSnapshotV1StructureTest {

    @Test
    @DisplayName("S1 최상위 스냅샷이 합의한 구성요소를 순서대로 제공한다")
    void exposesTopLevelSnapshotComponents() {
        assertThat(QuestionSnapshotV1.CURRENT_SCHEMA_VERSION).isEqualTo(1);
        assertThat(componentNames(QuestionSnapshotV1.class)).containsExactly(
                "schemaVersion",
                "metadata",
                "contentBlocks",
                "assets",
                "choices",
                "steps",
                "answerUnits",
                "explanation",
                "learningGuide",
                "rubricItems"
        );
    }

    @Test
    @DisplayName("S1 하위 record가 에이전트 공용 계약 필드를 제공한다")
    void exposesNestedSnapshotComponents() {
        assertThat(componentNames(SnapshotMetadata.class)).containsExactly(
                "questionType", "presentation", "difficulty", "subUnitId",
                "topicCode", "evaluationArea", "derivedFromQuestionId"
        );
        assertThat(componentNames(SnapshotContentBlock.class)).containsExactly(
                "blockKey", "blockKind", "displayOrder", "text", "assetRef", "markup"
        );
        assertThat(componentNames(SnapshotAssetReference.class)).containsExactly(
                "assetKey", "altText"
        );
        assertThat(componentNames(SnapshotChoice.class)).containsExactly(
                "choiceKey", "displayOrder", "content"
        );
        assertThat(componentNames(SnapshotStep.class)).containsExactly(
                "stepKey", "displayOrder", "label", "segments"
        );
        assertThat(componentNames(SnapshotSegment.class)).containsExactly(
                "type", "text", "unitKey"
        );
        assertThat(componentNames(SnapshotAnswerUnit.class)).containsExactly(
                "unitKey", "stepKey", "displayOrder", "answerRaw", "answerNormalized",
                "compareMethod", "diagnosticType", "displayUnit"
        );
        assertThat(componentNames(SnapshotRubricItem.class)).containsExactly(
                "rubricKey", "displayOrder", "criterion", "weightPercent"
        );
        assertThat(componentNames(SnapshotLearningGuide.class)).containsExactly(
                "conceptTitle", "summary", "keyPoints"
        );
    }

    @Test
    @DisplayName("S1 블록과 세그먼트 enum이 허용된 값만 제공한다")
    void exposesSupportedEnumValues() {
        assertThat(SnapshotBlockKind.values()).containsExactly(
                SnapshotBlockKind.TEXT,
                SnapshotBlockKind.FIGURE,
                SnapshotBlockKind.TABLE
        );
        assertThat(SnapshotSegmentType.values()).containsExactly(
                SnapshotSegmentType.TEXT,
                SnapshotSegmentType.BLANK,
                SnapshotSegmentType.ANSWER_REF
        );
    }

    private List<String> componentNames(Class<?> recordType) {
        assertThat(recordType.isRecord()).isTrue();
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
