package com.cenedu.backend.domain.problem.authoring.diagram;
import static org.assertj.core.api.Assertions.*; import java.util.*; import org.junit.jupiter.api.Test;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticValueType;
class DiagramSpecValidatorTest {
    private final DiagramSpecValidator v=new DiagramSpecValidator();
    private DiagramStyle style(){return new DiagramStyle("#000000","#FFFFFF","#FF0000",1,"sans-serif",12);}
    private DiagramViewport view(){return new DiagramViewport(400,300,16);}
    private SemanticResolvedValue n(int value){return new SemanticResolvedValue(SemanticValueType.INTEGER,Integer.toString(value),null);}

    @Test void rejectsExternalMarkupInLabels(){var d=new NumberLineDiagramSpecV1(1,"LINE",DiagramKind.NUMBER_LINE,view(),style(),"MIN","MAX","STEP",List.of(new NumberLinePointSpec("P","X","<script src=https://x>",PointMarker.CLOSED_CIRCLE)),List.of(),false,false);assertThatThrownBy(()->v.validate(d,Map.of())).isInstanceOf(DiagramValidationException.class);}

    @Test void rejectsUnsafeLabelInPlanePolygon(){
        var points = List.of(new PlanePointSpec("A","AX","AY","A"), new PlanePointSpec("B","BX","BY","B"),
                new PlanePointSpec("C","CX","CY","C"));
        var polygon = new PlanePolygonSpec("T", List.of("A","B","C"), true, "<script>alert(1)</script>");
        var d = new PlaneGeometryDiagramSpecV1(1,"P",DiagramKind.PLANE_GEOMETRY, view(), style(),
                points, List.of(), List.of(), List.of(polygon), List.of(), List.of(), List.of());
        var values = Map.of("AX", n(1), "AY", n(1), "BX", n(4), "BY", n(1), "CX", n(2), "CY", n(3));
        assertThatThrownBy(() -> v.validate(d, values)).isInstanceOf(DiagramValidationException.class);
    }

    @Test void rejectsUnsafeLabelInSolidGeometry(){
        var label = new SolidLabelSpec("L", "R", "<script>alert(1)</script>");
        var d = new SolidGeometryDiagramSpecV1(1,"S",DiagramKind.SOLID_GEOMETRY, view(), style(),
                SolidGeometryKind.CYLINDER, null, null, "H", "R", null, null, List.of(label));
        var values = Map.of("H", n(10), "R", n(5));
        assertThatThrownBy(() -> v.validate(d, values)).isInstanceOf(DiagramValidationException.class);
    }
}
