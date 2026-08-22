package com.cenedu.backend.domain.problem.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** production 기본값과 local experimental override가 실제 ConfigurationProperties에 주입되는지 검증한다. */
class ProblemVisualAuthoringPropertiesBindingTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProblemVisualAuthoringConfig.class);

    @Test
    void productionDefaultsAllowOnlyCoordinateGraphAndTable() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(ProblemVisualAuthoringProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.allowedKinds())
                    .containsExactlyInAnyOrder(DiagramKind.COORDINATE_GRAPH, DiagramKind.DATA_TABLE);
        });
    }

    @Test
    void localExperimentalOverrideBindsAllFiveFamilies() {
        contextRunner.withPropertyValues(
                "app.problem-authoring.visual.enabled=true",
                "app.problem-authoring.visual.allowed-kinds=NUMBER_LINE,COORDINATE_GRAPH,DATA_TABLE,PLANE_GEOMETRY,SOLID_GEOMETRY"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(ProblemVisualAuthoringProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.allowedKinds()).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(DiagramKind.class));
        });
    }
}
