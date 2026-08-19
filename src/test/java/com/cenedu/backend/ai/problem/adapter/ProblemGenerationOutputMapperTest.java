package com.cenedu.backend.ai.problem.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
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

    @Test
    void ignoresDisplayOrderAddedByModelToContentBlock() throws Exception {
        var output = json.readValue("""
                {"question":"문제","contentBlocks":[{"blockKind":"TEXT","text":"문제",
                 "displayOrder":0}],"explanation":"설명",
                 "learningGuide":{"conceptTitle":"개념","summary":"요약","keyPoints":["핵심"]},
                 "answerUnits":[{"answerRaw":"1","compareMethod":"VALUE"}]}
                """, ProblemGenerationOutput.class);

        var draft = mapper.map(command(UUID.randomUUID(), QuestionType.SHORT_INPUT), output);

        assertEquals("문제", draft.snapshot().contentBlocks().getFirst().text());
    }

    @Test
    void normalizesStepFillBlankKeysByDisplayOrder() throws Exception {
        var output = json.readValue("""
                {"question":"2+3을 단계대로 계산하시오.",
                 "contentBlocks":[{"blockKind":"TEXT","text":"2+3을 단계대로 계산하시오."}],
                 "steps":[
                   {"label":"첫째 값","segments":[
                     {"type":"TEXT","text":"2","answerUnitIndex":0},
                     {"type":"BLANK","text":null,"answerUnitIndex":0}]},
                   {"label":"둘째 값","segments":[
                     {"type":"TEXT","text":"3","answerUnitIndex":0},
                     {"type":"BLANK","text":null,"answerUnitIndex":0}]}
                 ],
                 "answerUnits":[
                   {"stepIndex":0,"answerRaw":"2","compareMethod":"VALUE","diagnosticType":"INTERPRET"},
                   {"stepIndex":0,"answerRaw":"5","compareMethod":"VALUE","diagnosticType":"ANSWER"}
                 ],
                 "explanation":"2와 3을 더하면 5다.",
                 "learningGuide":{"conceptTitle":"덧셈","summary":"두 수를 더한다.","keyPoints":["덧셈 규칙"]}}
                """, ProblemGenerationOutput.class);

        var draft = mapper.map(command(UUID.randomUUID(), QuestionType.STEP_FILL), output);

        assertEquals("B1", draft.snapshot().steps().get(0).segments().get(1).unitKey());
        assertEquals("B2", draft.snapshot().steps().get(1).segments().get(1).unitKey());
        assertNull(draft.snapshot().steps().get(0).segments().getFirst().unitKey());
        assertEquals("ST1", draft.snapshot().answerUnits().get(0).stepKey());
        assertEquals("ST2", draft.snapshot().answerUnits().get(1).stepKey());
        assertDoesNotThrow(() -> new SnapshotStructuralValidator().validate(draft.snapshot()));
    }

    private ProblemGenerationCommand command(UUID requestId, QuestionType type) {
        return new ProblemGenerationCommand(requestId, GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(type, "mid", null, List.of()),
                new CurriculumContext(1L, 1, 1, "대단원", "중단원", "소단원"), List.of(), List.of());
    }
}
