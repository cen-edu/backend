package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticExtractionPort;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.DefaultProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.*;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import org.springframework.stereotype.Component;

/** 시스템이 호출하는 legacy semantic extraction 경로이며 Dispatcher를 거치지 않는다. */
@Component
public class ProblemSemanticExtractionAdapter implements ProblemSemanticExtractionPort {
    private final LlmClient client;
    private final ProblemSemanticExtractionPromptFactory prompts;
    private final ProblemSemanticOutputParser parser;
    private final ProblemSemanticMaterializer materializer;

    public ProblemSemanticExtractionAdapter(LlmClient client,
            ProblemSemanticExtractionPromptFactory prompts,
            ProblemSemanticOutputParser parser) {
        this(client, prompts, parser, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProblemSemanticExtractionAdapter(LlmClient client,
            ProblemSemanticExtractionPromptFactory prompts,
            ProblemSemanticOutputParser parser,
            ProblemSemanticMaterializer materializer) {
        this.client = client; this.prompts = prompts; this.parser = parser; this.materializer = materializer;
    }

    @Override
    public SemanticExtractionResult extract(SemanticExtractionCommand command) {
        try {
            var response = client.completeStructured(prompts.systemPrompt(), prompts.messages(command),
                    ProblemStructuredOutputSchemas.SEMANTIC_MODEL);
            ProblemSemanticModelV1 model = parser.parse(response.text());
            if (materializer != null) {
                try { materializer.materialize(model); }
                catch (RuntimeException exception) {
                    String message = exception.getMessage();
                    if (message != null && (message.contains("지원하지") || message.contains("operation")
                            || message.contains("diagram"))) {
                        return new SemanticExtractionResult(SemanticExtractionStatus.UNSUPPORTED, null,
                                java.util.List.of("지원하지 않는 operation 또는 diagram입니다."));
                    }
                    return new SemanticExtractionResult(SemanticExtractionStatus.INVALID_SOURCE, null,
                            java.util.List.of("materialized answer가 원본과 일치하지 않습니다."));
                }
            }
            return new SemanticExtractionResult(SemanticExtractionStatus.EXTRACTED, model, java.util.List.of());
        } catch (IllegalArgumentException e) {
            return new SemanticExtractionResult(SemanticExtractionStatus.INVALID_SOURCE, null,
                    java.util.List.of("semantic model을 원본에 적용할 수 없습니다."));
        } catch (RuntimeException e) {
            return new SemanticExtractionResult(SemanticExtractionStatus.TECHNICAL_ERROR, null,
                    java.util.List.of("extraction provider 오류"));
        }
    }
}
