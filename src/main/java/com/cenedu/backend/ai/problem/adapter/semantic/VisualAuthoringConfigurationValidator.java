package com.cenedu.backend.ai.problem.adapter.semantic;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import com.cenedu.backend.domain.problem.config.ProblemVisualAuthoringProperties;

/** visual 기능이 semantic 저작 없이 조용히 Legacy로 우회되지 않도록 기동 설정을 검증한다. */
@Component
public class VisualAuthoringConfigurationValidator {
    private final ProblemVisualAuthoringProperties visualProperties;
    private final SemanticAuthoringProperties semanticProperties;

    public VisualAuthoringConfigurationValidator(ProblemVisualAuthoringProperties visualProperties,
            SemanticAuthoringProperties semanticProperties) {
        this.visualProperties = visualProperties;
        this.semanticProperties = semanticProperties;
    }

    /** 시각 저작 기능과 semantic 저작 기능의 토글 조합을 검증한다. */
    @PostConstruct
    public void validate() {
        if (visualProperties.enabled() && !semanticProperties.enabled()) {
            throw new IllegalStateException(
                    "PROBLEM_VISUAL_AUTHORING_ENABLED=true requires PROBLEM_SEMANTIC_AUTHORING_ENABLED=true");
        }
        if (visualProperties.enabled()
                && (visualProperties.allowedKinds().isEmpty() || visualProperties.allowedQuestionTypes().isEmpty())) {
            throw new IllegalStateException("활성화된 visual authoring에는 diagram과 question type allowlist가 필요합니다.");
        }
    }
}
