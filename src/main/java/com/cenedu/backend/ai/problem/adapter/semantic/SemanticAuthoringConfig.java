package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.DefaultProblemSemanticMaterializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SemanticAuthoringProperties.class)
public class SemanticAuthoringConfig {
    @Bean
    ProblemSemanticMaterializer problemSemanticMaterializer() { return new DefaultProblemSemanticMaterializer(); }
}
