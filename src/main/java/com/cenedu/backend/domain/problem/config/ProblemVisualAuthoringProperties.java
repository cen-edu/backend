package com.cenedu.backend.domain.problem.config;

import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** 시각 자산 생성 기능의 활성화 여부와 서버 allowlist를 보유한다. */
@ConfigurationProperties(prefix = "app.problem-authoring.visual")
public record ProblemVisualAuthoringProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue({"COORDINATE_GRAPH", "DATA_TABLE"}) Set<DiagramKind> allowedKinds,
        @DefaultValue({"MULTIPLE_CHOICE", "SHORT_INPUT"}) Set<QuestionType> allowedQuestionTypes,
        @DefaultValue("1") int maxAssetsPerQuestion,
        @DefaultValue("false") boolean forceRequired
) {
    public ProblemVisualAuthoringProperties(boolean enabled, Set<DiagramKind> allowedKinds,
                                            Set<QuestionType> allowedQuestionTypes, int maxAssetsPerQuestion) {
        this(enabled, allowedKinds, allowedQuestionTypes, maxAssetsPerQuestion, false);
    }

    @ConstructorBinding
    public ProblemVisualAuthoringProperties {
        allowedKinds = allowedKinds == null ? Set.of() : Set.copyOf(allowedKinds);
        allowedQuestionTypes = allowedQuestionTypes == null ? Set.of() : Set.copyOf(allowedQuestionTypes);
        if (maxAssetsPerQuestion < 1) {
            throw new IllegalArgumentException("문항당 시각 자산 최대 개수는 1 이상이어야 합니다.");
        }
    }
}
