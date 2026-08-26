package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticExtractionPort;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.DefaultProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.*;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.semantic.validation.SemanticObservableDependencyValidator;
import org.springframework.stereotype.Component;

/** 시스템이 호출하는 legacy semantic extraction 경로이며 Dispatcher를 거치지 않는다. */
@Component
public class ProblemSemanticExtractionAdapter implements ProblemSemanticExtractionPort {
    private final LlmClient client;
    private final ProblemSemanticExtractionPromptFactory prompts;
    private final ProblemSemanticOutputParser parser;
    private final ProblemSemanticMaterializer materializer;
    private final SemanticObservableDependencyValidator observableDependencies =
            new SemanticObservableDependencyValidator();

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
                try {
                    materializer.materialize(model);
                    observableDependencies.validate(model);
                } catch (IllegalArgumentException validation) {
                    return correctOnce(command, validation);
                }
                catch (RuntimeException exception) {
                    return failure("materialize", exception);
                }
            }
            return new SemanticExtractionResult(SemanticExtractionStatus.EXTRACTED, model, java.util.List.of());
        } catch (IllegalArgumentException e) {
            return new SemanticExtractionResult(SemanticExtractionStatus.INVALID_SOURCE, null,
                    java.util.List.of(ExtractionFinding.of("parse", e)));
        } catch (RuntimeException e) {
            return new SemanticExtractionResult(SemanticExtractionStatus.TECHNICAL_ERROR, null,
                    java.util.List.of(ExtractionFinding.of("provider", e)));
        }
    }

    /** domain validation 실패는 동일 Snapshot으로 한 번만 교정하고 다시 실패하면 종료한다. */
    private SemanticExtractionResult correctOnce(
            SemanticExtractionCommand command,
            IllegalArgumentException validation
    ) {
        String finding = ExtractionFinding.of("materialize", validation);
        try {
            var response = client.completeStructured(prompts.systemPrompt(),
                    prompts.correctionMessages(command, finding),
                    ProblemStructuredOutputSchemas.SEMANTIC_MODEL);
            ProblemSemanticModelV1 corrected = parser.parse(response.text());
            materializer.materialize(corrected);
            observableDependencies.validate(corrected);
            return new SemanticExtractionResult(SemanticExtractionStatus.EXTRACTED,
                    corrected, java.util.List.of("semantic validation 1회 교정 완료"));
        } catch (IllegalArgumentException exception) {
            return failure("repair-materialize", exception);
        } catch (RuntimeException exception) {
            return new SemanticExtractionResult(SemanticExtractionStatus.TECHNICAL_ERROR, null,
                    java.util.List.of(ExtractionFinding.of("repair-provider", exception)));
        }
    }

    private SemanticExtractionResult failure(String stage, RuntimeException exception) {
        String message = exception.getMessage();
        boolean unsupported = message != null && (message.contains("지원하지")
                || message.contains("operation") || message.contains("diagram"));
        return new SemanticExtractionResult(
                unsupported ? SemanticExtractionStatus.UNSUPPORTED : SemanticExtractionStatus.INVALID_SOURCE,
                null, java.util.List.of(ExtractionFinding.of(stage, exception)));
    }
}
