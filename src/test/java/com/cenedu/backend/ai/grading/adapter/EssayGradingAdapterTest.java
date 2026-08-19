package com.cenedu.backend.ai.grading.adapter;

import java.util.List;

import com.cenedu.backend.ai.grading.adapter.tool.GradingMathTools;
import com.cenedu.backend.domain.grading.port.EssayGradingCommand;
import com.cenedu.backend.domain.grading.port.EssayGradingStatus;
import com.cenedu.backend.domain.grading.port.RubricCriterion;
import com.cenedu.backend.domain.grading.port.RubricJudgement;
import com.cenedu.backend.domain.grading.port.RubricVerdict;
import com.cenedu.backend.domain.grading.service.ExpressionEvaluator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 응답 검증 전수표. <b>LLM 을 부르지 않는다</b> — 모델은 정해진 문자열을 돌려주는 대역이다.
 *
 * <p>여기서 보는 것은 "모델이 잘 판정하는가" 가 아니라 <b>모델이 무엇을 내든 우리가 안전하게
 * 받는가</b> 다. 판정 품질은 단계 4 의 측정 대상이고, 그건 이 테스트로 잡을 수 없다.
 */
class EssayGradingAdapterTest {

    private static final String IMAGE_URL = "https://example.invalid/answer.png";

    private final OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
    private final EssayGradingAdapter adapter = new EssayGradingAdapter(
            chatModel,
            OpenAiChatOptions.builder().model("test-model").build(),
            new ObjectMapper(),
            new GradingMathTools(new ExpressionEvaluator()),
            // 이 테스트는 run(command, withTools) 로 군을 직접 지정하므로 이 값을 타지 않는다.
            // 운영 기본값과 같게 둔다.
            false);

    private static final EssayGradingCommand TWO_ITEMS = new EssayGradingCommand(
            IMAGE_URL,
            List.of(new RubricCriterion(11, "소수로 나누는 과정을 썼다"),
                    new RubricCriterion(12, "지수를 써서 정리했다")));

    private void modelReplies(String... texts) {
        ChatResponse[] responses = new ChatResponse[texts.length];
        for (int i = 0; i < texts.length; i++) {
            responses[i] = new ChatResponse(List.of(new Generation(new AssistantMessage(texts[i]))));
        }
        ChatResponse[] rest = new ChatResponse[responses.length - 1];
        System.arraycopy(responses, 1, rest, 0, rest.length);
        when(chatModel.call(any(Prompt.class))).thenReturn(responses[0], rest);
    }

    @Test
    @DisplayName("약속한 JSON 이면 전사와 판정을 그대로 읽는다")
    void readsTranscriptionAndJudgements() {
        modelReplies("""
                {"transcription": "126 = 2 x 63", "items": [
                  {"rubricItemId": 11, "verdict": "SATISFIED", "evidence": "2로 나눈 줄이 있다"},
                  {"rubricItemId": 12, "verdict": "NOT_SATISFIED", "evidence": "지수 표기가 없다"}]}""");

        EssayGradingRun run = adapter.run(TWO_ITEMS, false);

        assertThat(run.result().status()).isEqualTo(EssayGradingStatus.JUDGED);
        assertThat(run.result().transcription()).isEqualTo("126 = 2 x 63");
        assertThat(run.result().judgements())
                .extracting(RubricJudgement::rubricItemId, RubricJudgement::verdict)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(11L, RubricVerdict.SATISFIED),
                        org.assertj.core.groups.Tuple.tuple(12L, RubricVerdict.NOT_SATISFIED));
        assertThat(run.trace().modelCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("코드 펜스가 붙어 와도 읽는다 — 프롬프트로 금지해도 붙는다")
    void stripsCodeFence() {
        modelReplies("""
                ```json
                {"transcription": "x=2", "items": [
                  {"rubricItemId": 11, "verdict": "SATISFIED", "evidence": ""},
                  {"rubricItemId": 12, "verdict": "SATISFIED", "evidence": ""}]}
                ```""");

        assertThat(adapter.run(TWO_ITEMS, false).result().isJudged()).isTrue();
    }

    @Test
    @DisplayName("우리가 준 목록에 없는 rubricItemId 는 버린다")
    void dropsUnknownRubricItemId() {
        modelReplies("""
                {"transcription": "", "items": [
                  {"rubricItemId": 11, "verdict": "SATISFIED", "evidence": ""},
                  {"rubricItemId": 12, "verdict": "SATISFIED", "evidence": ""},
                  {"rubricItemId": 99, "verdict": "SATISFIED", "evidence": "없는 기준"}]}""");

        EssayGradingRun run = adapter.run(TWO_ITEMS, false);

        assertThat(run.result().judgements()).extracting(RubricJudgement::rubricItemId)
                .containsExactly(11L, 12L);
        assertThat(run.trace().droppedItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("알 수 없는 verdict 도 버린다 — 없는 값을 충족·미충족 어느 쪽으로도 밀지 않는다")
    void dropsUnknownVerdict() {
        modelReplies("""
                {"transcription": "", "items": [
                  {"rubricItemId": 11, "verdict": "PARTIAL", "evidence": ""},
                  {"rubricItemId": 12, "verdict": "SATISFIED", "evidence": ""}]}""",
                """
                {"transcription": "", "items": [
                  {"rubricItemId": 11, "verdict": "NOT_SATISFIED", "evidence": ""},
                  {"rubricItemId": 12, "verdict": "SATISFIED", "evidence": ""}]}""");

        EssayGradingRun run = adapter.run(TWO_ITEMS, false);

        assertThat(run.trace().droppedItems()).isEqualTo(1);
        assertThat(run.result().isJudged()).isTrue();
        assertThat(run.trace().modelCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 항목이 두 번 오면 먼저 온 판정을 남긴다")
    void keepsFirstJudgementForDuplicateItem() {
        modelReplies("""
                {"transcription": "", "items": [
                  {"rubricItemId": 11, "verdict": "SATISFIED", "evidence": "처음"},
                  {"rubricItemId": 11, "verdict": "NOT_SATISFIED", "evidence": "번복"},
                  {"rubricItemId": 12, "verdict": "SATISFIED", "evidence": ""}]}""");

        assertThat(adapter.run(TWO_ITEMS, false).result().judgements())
                .extracting(RubricJudgement::verdict)
                .containsExactly(RubricVerdict.SATISFIED, RubricVerdict.SATISFIED);
    }

    @Test
    @DisplayName("판정이 빠진 항목이 있으면 다시 물어보고, 채워지면 완료다")
    void asksAgainForMissingItems() {
        modelReplies("""
                {"transcription": "", "items": [
                  {"rubricItemId": 11, "verdict": "SATISFIED", "evidence": ""}]}""",
                """
                {"transcription": "다시 읽음", "items": [
                  {"rubricItemId": 11, "verdict": "SATISFIED", "evidence": ""},
                  {"rubricItemId": 12, "verdict": "UNJUDGEABLE", "evidence": "지워진 자국뿐"}]}""");

        EssayGradingRun run = adapter.run(TWO_ITEMS, false);

        assertThat(run.result().status()).isEqualTo(EssayGradingStatus.JUDGED);
        assertThat(run.result().judgements()).extracting(RubricJudgement::verdict)
                .containsExactly(RubricVerdict.SATISFIED, RubricVerdict.UNJUDGEABLE);
        assertThat(run.trace().modelCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("끝까지 판정이 다 붙지 않으면 억지로 만들지 않고 TURN_LIMIT_REACHED")
    void stopsAtTurnLimitWithoutInventingJudgements() {
        modelReplies("""
                {"transcription": "읽은 데까지", "items": [
                  {"rubricItemId": 11, "verdict": "SATISFIED", "evidence": ""}]}""");

        EssayGradingRun run = adapter.run(TWO_ITEMS, false);

        assertThat(run.result().status()).isEqualTo(EssayGradingStatus.TURN_LIMIT_REACHED);
        assertThat(run.result().judgements()).hasSize(1);
        assertThat(run.result().transcription()).isEqualTo("읽은 데까지");
        assertThat(run.trace().modelCalls()).isEqualTo(8);
    }

    @Test
    @DisplayName("JSON 이 아니면 MALFORMED_OUTPUT — 억지로 살리지 않는다")
    void reportsMalformedOutput() {
        modelReplies("판정을 못 하겠습니다.");

        EssayGradingRun run = adapter.run(TWO_ITEMS, false);

        assertThat(run.result().status()).isEqualTo(EssayGradingStatus.MALFORMED_OUTPUT);
        assertThat(run.result().judgements()).isEmpty();
        assertThat(run.trace().malformedOutputs()).isEqualTo(8);
    }
}
