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
                    boolean unsupported = message != null && (message.contains("지원하지")
                            || message.contains("operation") || message.contains("diagram"));
                    // 원래 예외 메시지를 그대로 남긴다. 고정 문구로 덮어쓰면 왜 실패했는지
                    // (placeholder 누락인지, 정답 불일치인지, 지원하지 않는 구성인지) 알 수 없어
                    // 추출 실패가 쌓여도 원인을 좁힐 수 없다.
                    return new SemanticExtractionResult(
                            unsupported ? SemanticExtractionStatus.UNSUPPORTED : SemanticExtractionStatus.INVALID_SOURCE,
                            null, java.util.List.of(ExtractionFinding.of("materialize", exception)));
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
}
