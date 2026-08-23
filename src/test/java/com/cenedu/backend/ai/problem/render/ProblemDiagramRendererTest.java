package com.cenedu.backend.ai.problem.render;
import static org.assertj.core.api.Assertions.*; import com.cenedu.backend.ai.problem.adapter.SafeSvgSanitizer; import com.cenedu.backend.domain.problem.authoring.diagram.*; import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue; import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticValueType; import java.util.*; import org.junit.jupiter.api.Test;
class ProblemDiagramRendererTest { @Test void identicalSpecProducesIdenticalSvgAndHash(){var s=new NumberLineDiagramSpecV1(1,"LINE",DiagramKind.NUMBER_LINE,new DiagramViewport(640,180,16),new DiagramStyle("#000000","#FFFFFF","#FF0000",1,"sans-serif",12),"MIN","MAX","STEP",List.of(),List.of(),false,false);var r=new ProblemDiagramRenderer(new SafeSvgSanitizer());var values=Map.of("MIN",n(-5),"MAX",n(5),"STEP",n(1));var a=r.render(s,new DiagramRenderContext(values));var b=r.render(s,new DiagramRenderContext(values));assertThat(b.svg()).isEqualTo(a.svg());assertThat(b.sha256()).isEqualTo(a.sha256());assertThat(a.svg()).contains("viewBox=\"0 0 640 180\"");}

    @Test void planeGeometryLabelWithXmlSpecialCharsIsEscaped(){
        var style = new DiagramStyle("#000000","#FFFFFF","#FF0000",1,"sans-serif",12);
        var viewport = new DiagramViewport(400,240,16);
        var points = List.of(new PlanePointSpec("A","AX","AY","A"), new PlanePointSpec("B","BX","BY","B"),
                new PlanePointSpec("C","CX","CY","C"));
        var polygon = new PlanePolygonSpec("T", List.of("A","B","C"), true, "A & B < C");
        var spec = new PlaneGeometryDiagramSpecV1(1,"P",DiagramKind.PLANE_GEOMETRY, viewport, style,
                points, List.of(), List.of(), List.of(polygon), List.of(), List.of(), List.of());
        var values = Map.of("AX", n(1), "AY", n(1), "BX", n(4), "BY", n(1), "CX", n(2), "CY", n(3));
        var r = new ProblemDiagramRenderer(new SafeSvgSanitizer());

        var result = r.render(spec, new DiagramRenderContext(values));

        assertThat(result.svg()).contains("A &amp; B &lt; C").doesNotContain("A & B < C");
    }

    private SemanticResolvedValue n(int value){return new SemanticResolvedValue(SemanticValueType.INTEGER,Integer.toString(value),null);} }
