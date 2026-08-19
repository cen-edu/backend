package com.cenedu.backend.domain.problem.authoring.semantic.model;
import static org.assertj.core.api.Assertions.*;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import java.util.List;
import org.junit.jupiter.api.Test;
class ProblemSemanticModelV1Test {
 @Test void exposesVersionOneAndCopiesLists() { var scope=new CurriculumScope("2022_REVISED","MIDDLE",1,1,"A",1L,"m","n","s"); var m=new ProblemSemanticModelV1(1,scope,new SemanticProblemIntent(null,"low",null,"", "DIAMETER",1,false),List.of(new SemanticParameter("RADIUS",SemanticValueType.INTEGER,"3","cm",true,null)),List.of(new SemanticComputation("DIAMETER",SemanticOperation.MULTIPLY,List.of("RADIUS"),"2","cm","6")),List.of(),new SemanticPresentationPlan("",List.of(),List.of(),"",null,List.of()),List.of(),List.of()); assertThat(m.schemaVersion()).isEqualTo(1); assertThat(m.parameters()).extracting(SemanticParameter::key).containsExactly("RADIUS"); assertThat(m.computations()).extracting(SemanticComputation::key).containsExactly("DIAMETER"); }
 @Test void rejectsNullCollections() { var scope=new CurriculumScope("2022_REVISED","MIDDLE",1,1,"A",1L,"m","n","s"); assertThatThrownBy(() -> new ProblemSemanticModelV1(1,scope,null,null,List.of(),List.of(),null,List.of(),List.of())).isInstanceOf(NullPointerException.class); }
}
