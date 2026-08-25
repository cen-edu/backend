package com.cenedu.backend.ai.problem.adapter.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.problem.adapter.ProblemGenerationOutputMapper;
import com.cenedu.backend.ai.problem.adapter.ProblemGenerationPromptFactory;
import com.cenedu.backend.ai.problem.adapter.ProblemImageGenerationLoop;
import com.cenedu.backend.ai.problem.adapter.SpringAiProblemGenerationAdapter;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotNormalizedValidator;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotValidationException;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationMode;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProblemGenerationVisualRoutingTest {

    @Test
    void 생성후보를_공통_이미지_생성_루프로_전달한다() {
        LlmClient client = mock(LlmClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> mapperProvider = mock(ObjectProvider.class);
        ProblemGenerationOutputMapper outputMapper = mock(ProblemGenerationOutputMapper.class);
        SnapshotStructuralValidator structural = mock(SnapshotStructuralValidator.class);
        SnapshotNormalizedValidator normalized = mock(SnapshotNormalizedValidator.class);
        ProblemImageGenerationLoop imageGenerationLoop = mock(ProblemImageGenerationLoop.class);
        ProblemCandidateDraft textCandidate = mock(ProblemCandidateDraft.class);
        ProblemCandidateDraft visualCandidate = mock(ProblemCandidateDraft.class);
        ProblemGenerationCommand command = command(VisualGenerationRequirement.none());

        when(mapperProvider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        when(client.completeStructured(anyString(), anyList(), anyString())).thenReturn(new LlmResponse("""
                {"choices":[{"content":"1"},{"content":"2"},{"content":"3"},{"content":"4"},{"content":"5"}],
                 "visualRequired":true,"visualKind":"COORDINATE_GRAPH",
                 "visualDescription":"점 A와 직선 y=2x"}
                """, 1, 1, 0));
        when(outputMapper.map(any(), any())).thenReturn(textCandidate);
        when(imageGenerationLoop.generate(eq(textCandidate), any(), eq(command)))
                .thenReturn(visualCandidate);

        var pipeline = new NonSemanticProblemGenerationPipeline(client, mapperProvider,
                new ProblemGenerationPromptFactory(), outputMapper, structural, normalized,
                imageGenerationLoop);

        assertThat(pipeline.generate(command)).isSameAs(visualCandidate);
        verify(imageGenerationLoop).generate(eq(textCandidate), any(), eq(command));
    }

    @Test
    void 검증실패후_직전후보를_보존해_다음교정호출에_전달한다() {
        LlmClient client = mock(LlmClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> mapperProvider = mock(ObjectProvider.class);
        ProblemGenerationOutputMapper outputMapper = mock(ProblemGenerationOutputMapper.class);
        SnapshotStructuralValidator structural = mock(SnapshotStructuralValidator.class);
        SnapshotNormalizedValidator normalized = mock(SnapshotNormalizedValidator.class);
        ProblemImageGenerationLoop imageGenerationLoop = mock(ProblemImageGenerationLoop.class);
        ProblemCandidateDraft corrected = mock(ProblemCandidateDraft.class);
        ProblemGenerationCommand command = command(VisualGenerationRequirement.none());

        when(mapperProvider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        when(client.completeStructured(anyString(), anyList(), anyString())).thenReturn(new LlmResponse("""
                {"question":"현재 후보","contentBlocks":[],
                 "choices":[{"content":"1"},{"content":"2"},{"content":"3"},{"content":"4"},{"content":"5"}],"steps":[],
                 "answerUnits":[],"explanation":"해설",
                 "learningGuide":{"conceptTitle":"개념","summary":"요약","keyPoints":["핵심"]},
                 "rubricItems":[],"assets":[],"visualRequired":false,
                 "visualKind":null,"visualDescription":null}
                """, 1, 1, 0));
        when(outputMapper.map(any(), any()))
                .thenThrow(new SnapshotValidationException(List.of("choices: 정답 보기가 없습니다.")))
                .thenReturn(corrected);
        when(imageGenerationLoop.generate(eq(corrected), any(), eq(command))).thenReturn(corrected);

        var pipeline = new NonSemanticProblemGenerationPipeline(client, mapperProvider,
                new ProblemGenerationPromptFactory(), outputMapper, structural, normalized,
                imageGenerationLoop);

        assertThat(pipeline.generate(command)).isSameAs(corrected);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(client, times(2)).completeStructured(anyString(), messages.capture(), anyString());
        String secondCall = messages.getAllValues().get(1).stream()
                .map(ChatMessage::content).collect(java.util.stream.Collectors.joining("\n"));
        assertThat(secondCall)
                .contains("PREVIOUS_CANDIDATE_JSON")
                .contains("현재 후보")
                .contains("choices: 정답 보기가 없습니다.");
    }

    @Test
    void 그래프누락_위반후_다음교정호출을_좌표그래프필수로_승격한다() {
        LlmClient client = mock(LlmClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> mapperProvider = mock(ObjectProvider.class);
        ProblemGenerationOutputMapper outputMapper = mock(ProblemGenerationOutputMapper.class);
        SnapshotStructuralValidator structural = mock(SnapshotStructuralValidator.class);
        SnapshotNormalizedValidator normalized = mock(SnapshotNormalizedValidator.class);
        ProblemImageGenerationLoop imageGenerationLoop = mock(ProblemImageGenerationLoop.class);
        ProblemCandidateDraft candidate = mock(ProblemCandidateDraft.class);
        ProblemGenerationCommand command = command(VisualGenerationRequirement.none());

        when(mapperProvider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        when(client.completeStructured(anyString(), anyList(), anyString())).thenReturn(new LlmResponse("""
                {"question":"다음 그래프를 보고 답하시오.","contentBlocks":[],
                 "choices":[{"content":"1"},{"content":"2"},{"content":"3"},{"content":"4"},{"content":"5"}],"steps":[],
                 "answerUnits":[],"explanation":"해설",
                 "learningGuide":{"conceptTitle":"개념","summary":"요약","keyPoints":["핵심"]},
                 "rubricItems":[],"assets":[],"visualRequired":false,
                 "visualKind":null,"visualDescription":null}
                """, 1, 1, 0));
        when(outputMapper.map(any(), any())).thenReturn(candidate);
        when(imageGenerationLoop.generate(eq(candidate), any(), any()))
                .thenThrow(new SnapshotValidationException(List.of(
                        "visualDependency: 실제 그림·그래프·표 없이 시각 자료를 참조할 수 없습니다.")))
                .thenReturn(candidate);

        var pipeline = new NonSemanticProblemGenerationPipeline(client, mapperProvider,
                new ProblemGenerationPromptFactory(), outputMapper, structural, normalized,
                imageGenerationLoop);

        assertThat(pipeline.generate(command)).isSameAs(candidate);
        ArgumentCaptor<ProblemGenerationCommand> commands =
                ArgumentCaptor.forClass(ProblemGenerationCommand.class);
        verify(imageGenerationLoop, times(2)).generate(eq(candidate), any(), commands.capture());
        assertThat(commands.getAllValues().get(0).specification().visualRequirement().mode())
                .isEqualTo(VisualGenerationMode.NONE);
        assertThat(commands.getAllValues().get(1).specification().visualRequirement())
                .isEqualTo(new VisualGenerationRequirement(
                        VisualGenerationMode.REQUIRED, VisualReferenceKind.COORDINATE_GRAPH));
    }

    @Test
    void explicitCoordinateGraphUsesNonSemanticPipelineWhenSemanticFeatureIsEnabled() {
        ProblemSemanticGenerationPipeline semantic = mock(ProblemSemanticGenerationPipeline.class);
        NonSemanticProblemGenerationPipeline nonSemantic = mock(NonSemanticProblemGenerationPipeline.class);
        ProblemGenerationCommand command = command(new VisualGenerationRequirement(
                VisualGenerationMode.REQUIRED, VisualReferenceKind.COORDINATE_GRAPH));
        ProblemCandidateDraft expected = mock(ProblemCandidateDraft.class);
        when(nonSemantic.generate(command)).thenReturn(expected);

        var adapter = new SpringAiProblemGenerationAdapter(
                new SemanticAuthoringProperties(true), semantic, nonSemantic);

        assertThat(adapter.generate(command)).isSameAs(expected);
        verify(nonSemantic).generate(command);
        verifyNoInteractions(semantic);
    }

    @Test
    void 신규객관식은_문제은행기준_5개보기로_교정한다() {
        LlmClient client = mock(LlmClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> mapperProvider = mock(ObjectProvider.class);
        ProblemGenerationOutputMapper outputMapper = mock(ProblemGenerationOutputMapper.class);
        SnapshotStructuralValidator structural = mock(SnapshotStructuralValidator.class);
        SnapshotNormalizedValidator normalized = mock(SnapshotNormalizedValidator.class);
        ProblemImageGenerationLoop imageGenerationLoop = mock(ProblemImageGenerationLoop.class);
        ProblemCandidateDraft candidate = mock(ProblemCandidateDraft.class);
        ProblemGenerationCommand command = command(VisualGenerationRequirement.none());

        when(mapperProvider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        when(client.completeStructured(anyString(), anyList(), anyString()))
                .thenReturn(new LlmResponse(candidateJson(4), 1, 1, 0))
                .thenReturn(new LlmResponse(candidateJson(5), 1, 1, 0));
        when(outputMapper.map(any(), any())).thenReturn(candidate);
        when(imageGenerationLoop.generate(eq(candidate), any(), eq(command))).thenReturn(candidate);

        var pipeline = new NonSemanticProblemGenerationPipeline(client, mapperProvider,
                new ProblemGenerationPromptFactory(), outputMapper, structural, normalized,
                imageGenerationLoop);

        assertThat(pipeline.generate(command)).isSameAs(candidate);
        verify(client, times(2)).completeStructured(anyString(), anyList(), anyString());
    }

    private static String candidateJson(int choiceCount) {
        String choices = java.util.stream.IntStream.rangeClosed(1, choiceCount)
                .mapToObj(index -> "{\"content\":\"" + index + "\"}")
                .collect(java.util.stream.Collectors.joining(","));
        return """
                {"question":"문제","contentBlocks":[],"choices":[%s],"steps":[],
                 "answerUnits":[],"explanation":"해설",
                 "learningGuide":{"conceptTitle":"개념","summary":"요약","keyPoints":["핵심"]},
                 "rubricItems":[],"assets":[],"visualRequired":false,
                 "visualKind":null,"visualDescription":null}
                """.formatted(choices);
    }

    private static ProblemGenerationCommand command(VisualGenerationRequirement visual) {
        return new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.COMPREHENSIVE_ASSESSMENT_SHORTAGE,
                new GenerationSpecification(QuestionType.MULTIPLE_CHOICE, "mid", null,
                        List.of(), false, visual),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 20L,
                        "변화와 관계", "좌표와 그래프", "좌표평면과 그래프"),
                List.of(), List.of());
    }
}
