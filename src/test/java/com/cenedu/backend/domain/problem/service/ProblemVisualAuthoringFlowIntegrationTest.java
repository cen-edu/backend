package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.cenedu.backend.ai.problem.adapter.LocalDraftAssetProductionAdapter;
import com.cenedu.backend.ai.problem.adapter.SafeSvgSanitizer;
import com.cenedu.backend.ai.problem.render.ProblemDiagramRenderer;
import com.cenedu.backend.domain.problem.authoring.asset.*;
import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticValueType;
import com.cenedu.backend.domain.problem.config.ProblemDraftStorageProperties;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringVerificationStatus;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** 외부 LLM·S3 없이 구조화 도식 생성부터 교사 preview data URL까지의 계약을 연결한다. */
class ProblemVisualAuthoringFlowIntegrationTest {
    @TempDir Path root;

    @Test
    void generatedSvgReadyArtifactIsExposedAsTeacherPreviewDataUrl() throws Exception {
        var sanitizer = new SafeSvgSanitizer();
        var adapter = new LocalDraftAssetProductionAdapter(root.toString(), sanitizer,
                new ProblemDiagramRenderer(sanitizer));
        var spec = new NumberLineDiagramSpecV1(1, "F1", DiagramKind.NUMBER_LINE,
                new DiagramViewport(640, 180, 16),
                new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12),
                "MIN", "MAX", "STEP", List.of(), List.of(), false, false);
        var values = Map.of("MIN", value(-5), "MAX", value(5), "STEP", value(1));
        var plan = new GeneratedAssetPlan("F1", AssetRole.FIGURE, AssetProductionMode.STRUCTURED_RENDER,
                AssetOutputFormat.SVG, "figure", new AssetGenerationSpecification(1, "figure",
                        List.of(), List.of(), Map.of(), values, spec));
        var artifact = adapter.produce(plan, new AssetProductionContext(7L, 2, QuestionType.MULTIPLE_CHOICE));
        assertThat(artifact.status()).isEqualTo(DraftAssetStatus.READY);

        var version = mock(ProblemAuthoringVersion.class);
        when(version.getVerificationStatus()).thenReturn(AuthoringVerificationStatus.PASSED);
        when(version.getAssetManifest()).thenReturn(new ObjectMapper().writeValueAsString(
                new DraftAssetManifest(1, List.of(plan), List.of(artifact))));
        var sessions = mock(ProblemAuthoringSessionRepository.class);
        when(sessions.findByIdAndOwnerTeacherId(2L, 7L)).thenReturn(Optional.of(mock(com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession.class)));
        var versions = mock(ProblemAuthoringVersionRepository.class);
        when(versions.findByIdAndSessionId(3L, 2L)).thenReturn(Optional.of(version));
        var properties = new ProblemDraftStorageProperties(root, 1_000_000);
        var preview = new ProblemDraftAssetPreviewService(sessions, versions,
                new ProblemDraftPathResolver(properties), properties, new ObjectMapper());

        var response = preview.preview(7L, 2L, 3L, "F1");
        assertThat(response.dataUrl()).startsWith("data:image/svg+xml;base64,");
        assertThat(response.checksum()).isEqualTo(artifact.checksum());
        assertThat(response.widthPx()).isEqualTo(640);
    }

    private SemanticResolvedValue value(int value) {
        return new SemanticResolvedValue(SemanticValueType.INTEGER, Integer.toString(value), null);
    }
}
