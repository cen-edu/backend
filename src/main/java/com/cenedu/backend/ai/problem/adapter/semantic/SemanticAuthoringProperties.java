package com.cenedu.backend.ai.problem.adapter.semantic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.problem-authoring.semantic")
public record SemanticAuthoringProperties(@DefaultValue("false") boolean enabled) { }
