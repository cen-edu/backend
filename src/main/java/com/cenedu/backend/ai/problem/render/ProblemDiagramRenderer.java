package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.domain.problem.authoring.port.*;
import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.ai.problem.adapter.SafeSvgSanitizer;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

import org.springframework.stereotype.Component;
import com.cenedu.backend.domain.problem.config.ProblemVisualAuthoringProperties;

@Component
public final class ProblemDiagramRenderer implements ProblemDiagramRendererPort {
    private final SafeSvgSanitizer sanitizer;
    private final ProblemVisualAuthoringProperties properties;

    public ProblemDiagramRenderer(SafeSvgSanitizer sanitizer) {
        this(sanitizer, new ProblemVisualAuthoringProperties(true,
                java.util.Set.of(DiagramKind.NUMBER_LINE, DiagramKind.COORDINATE_GRAPH, DiagramKind.DATA_TABLE,
                        DiagramKind.PLANE_GEOMETRY, DiagramKind.SOLID_GEOMETRY),
                java.util.Set.of(com.cenedu.backend.global.common.enums.QuestionType.MULTIPLE_CHOICE,
                        com.cenedu.backend.global.common.enums.QuestionType.SHORT_INPUT), 1));
    }

    public ProblemDiagramRenderer(SafeSvgSanitizer sanitizer, ProblemVisualAuthoringProperties properties) {
        this.sanitizer = sanitizer;
        this.properties = properties;
    }

    public RenderedDiagram render(DiagramSpecV1 s, DiagramRenderContext c) {
        if (s == null || c == null || s.viewport() == null || c.values() == null) throw new DiagramRenderException("도식 입력이 없습니다.");
        if (!properties.allowedKinds().contains(s.kind())) throw new DiagramRenderException("허용되지 않은 도식 유형입니다.");
        int w = s.viewport().width(), h = s.viewport().height();
        String body = "<rect x=\"0\" y=\"0\" width=\"" + w + "\" height=\"" + h + "\" fill=\"#FFFFFF\"/>";
        if (s instanceof NumberLineDiagramSpecV1 x) body += new NumberLineSvgRenderer().render(x, c.values());
        else if (s instanceof CoordinateGraphDiagramSpecV1 x)
            body += new CoordinateGraphSvgRenderer().render(x, c.values());
        else if (s instanceof PlaneGeometryDiagramSpecV1 x)
            body += new PlaneGeometrySvgRenderer().render(x, c.values());
        else if (s instanceof SolidGeometryDiagramSpecV1 x)
            body += new SolidGeometrySvgRenderer().render(x, c.values());
        else if (s instanceof DataTableDiagramSpecV1 x) body += new DataTableSvgRenderer().render(x, c.values());
        else throw new DiagramRenderException("지원하지 않는 도식 유형입니다.");
        String svg = sanitizer.sanitize(DeterministicSvgWriter.empty(w, h, body));
        try {
            return new RenderedDiagram(s.assetKey(), svg, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(svg.getBytes(StandardCharsets.UTF_8))), w, h, ProblemRendererVersion.CURRENT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
