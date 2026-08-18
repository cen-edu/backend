package com.cenedu.backend.ai.problem.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;

class ProblemGenerationOutputMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ProblemGenerationOutputMapper mapper = new ProblemGenerationOutputMapper();

    @Test
    void mapsShortInputAndCopiesRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();
        ProblemGenerationCommand command = command(requestId, QuestionType.SHORT_INPUT);
        var output = json.readValue("""
                {"question":"12를 구하시오.","explanation":"계산한다.",
                 "learningGuide":{"conceptTitle":"연산","summary":"연산 개념","keyPoints":["연산 규칙"]},
                 "answerUnits":[{"answerRaw":"12","compareMethod":"VALUE"}]}
                """, ProblemGenerationOutput.class);

        var draft = mapper.map(command, output);

        assertEquals(requestId, draft.requestId());
        assertEquals(QuestionType.SHORT_INPUT, draft.snapshot().metadata().questionType());
        assertEquals("12", draft.snapshot().answerUnits().getFirst().answerRaw());
        assertEquals("CB1", draft.snapshot().contentBlocks().getFirst().blockKey());
    }

    @Test
    void rejectsMissingLearningGuide() throws Exception {
        var output = json.readValue("{" +
                "\"question\":\"문제\",\"explanation\":\"설명\"}", ProblemGenerationOutput.class);

        assertThrows(IllegalArgumentException.class,
                () -> mapper.map(command(UUID.randomUUID(), QuestionType.SHORT_INPUT),
                        output));
    }

    private ProblemGenerationCommand command(UUID requestId, QuestionType type) {
        return new ProblemGenerationCommand(requestId, GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(type, "mid", null, List.of()),
                new CurriculumContext(1L, 1, 1, "대단원", "중단원", "소단원"), List.of(), List.of());
    }
}
