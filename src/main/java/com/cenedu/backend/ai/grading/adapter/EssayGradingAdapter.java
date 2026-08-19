package com.cenedu.backend.ai.grading.adapter;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cenedu.backend.ai.grading.adapter.EssayGradingRun.Trace;
import com.cenedu.backend.ai.grading.adapter.tool.GradingMathTools;
import com.cenedu.backend.domain.grading.port.EssayGradingCommand;
import com.cenedu.backend.domain.grading.port.EssayGradingPort;
import com.cenedu.backend.domain.grading.port.EssayGradingResult;
import com.cenedu.backend.domain.grading.port.EssayGradingStatus;
import com.cenedu.backend.domain.grading.port.RubricCriterion;
import com.cenedu.backend.domain.grading.port.RubricJudgement;
import com.cenedu.backend.domain.grading.port.RubricVerdict;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.completions.CompletionUsage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 서술형 채점 도구 루프(A 구조).
 *
 * <pre>
 * 필기 이미지 + 기준 목록 → [VLM] → math 도구 호출? → 실행 → 결과를 붙여 다시 [VLM] → … → 판정 JSON
 * </pre>
 *
 * <p><b>전사 단계를 앞에 두지 않는다</b>(D1·D3). 이미지를 그대로 넣고, 전사는 판정과 같은 응답에서
 * 받는다. 전사를 따로 돌리면 그 출력이 판정의 입력이 돼, 전사 실패가 곧 판정 실패로 굳는다.
 *
 * <p><b>도구 실행을 Spring AI 에 맡기지 않고 직접 돈다.</b> 맡기면 루프가 라이브러리 안에서 돌아
 * 턴 상한·도구 상태 분포·차수별 토큰을 넣을 자리가 없다. 그 셋이 단계 4~5 의 측정 대상이다.
 * ({@code ConceptChatLoopEngine} 과 같은 이유·같은 방식이다.)
 *
 * <p><b>{@code LlmClient} 를 쓰지 않는다.</b> {@code ChatMessage}(이동규 소유)에 이미지를 넣을
 * 자리가 없고, 인터페이스를 바꾸면 검증 세션과의 유일한 접점을 건드린다.
 *
 * <p><b>학생 답안 원문·전사·도구 인자를 로그에 남기지 않는다.</b> 남기는 것은 개수와 상태뿐이다.
 */
@Component
public class EssayGradingAdapter implements EssayGradingPort {

    private static final Logger log = LoggerFactory.getLogger(EssayGradingAdapter.class);

    /**
     * LLM 호출 차수 상한. 개념 챗봇 루프 실측 6~7턴을 보고 8 에서 시작한다(단계 2).
     *
     * <p>상한에 닿으면 판정을 억지로 내지 않고 실패로 둔다 — 근거 없는 판정이 점수가 되면
     * 그 칸은 교사가 다시 볼 이유를 잃는다.
     */
    private static final int MAX_MODEL_CALLS = 8;

    private static final String USER_INSTRUCTION = "이 학생 답안을 채점 기준 항목별로 판정하라.";

    private static final String RETRY_JSON =
            "약속한 JSON 하나만 출력한다. 설명도 코드 블록 표시도 붙이지 않는다.";

    private final OpenAiChatModel chatModel;
    private final OpenAiChatOptions baseOptions;
    private final ObjectMapper objectMapper;
    private final List<ToolCallback> toolCallbacks;
    private final Map<String, ToolCallback> toolsByName;

    /**
     * <b>파라미터 이름이 빈 이름이다.</b> {@code OpenAiChatModel} 빈이 둘이라 Spring 이 타입으로
     * 고르지 못하고 파라미터 이름으로 고른다. 이름이 어긋나면 기동이 실패한다.
     */
    public EssayGradingAdapter(OpenAiChatModel loopChatModel,
                               OpenAiChatOptions loopChatOptions,
                               ObjectMapper objectMapper,
                               GradingMathTools gradingMathTools) {
        this.chatModel = loopChatModel;
        this.baseOptions = loopChatOptions;
        this.objectMapper = objectMapper;
        this.toolCallbacks = List.of(ToolCallbacks.from(gradingMathTools));

        Map<String, ToolCallback> byName = new LinkedHashMap<>();
        this.toolCallbacks.forEach(callback -> byName.put(callback.getToolDefinition().name(), callback));
        this.toolsByName = Map.copyOf(byName);
    }

    @Override
    public EssayGradingResult grade(EssayGradingCommand command) {
        return run(command, true).result();
    }

    /**
     * 측정용 진입점. {@code withTools} 가 단계 4 의 두 군을 가른다 — B 군은 도구 목록을 비운
     * 단일 호출이고, 그 밖의 모든 것(프롬프트·출력 순서·파싱)은 A 군과 같은 코드를 탄다.
     */
    public EssayGradingRun run(EssayGradingCommand command, boolean withTools) {
        return run(command, withTools, null);
    }

    /**
     * seed 를 고정해 부르는 측정용 진입점(D18).
     *
     * <p><b>운영은 seed 를 쓰지 않는다.</b> 재현성은 운영이 요구한 적 없고, 고정하면 그 seed 가
     * 특별히 잘 맞는(또는 안 맞는) 경우를 실력으로 착각하게 된다. 두 군을 비교할 때만 노이즈를
     * 줄이려고 쓴다.
     *
     * @param seed {@code null} 이면 싣지 않는다
     */
    public EssayGradingRun run(EssayGradingCommand command, boolean withTools, Integer seed) {
        long startedAt = System.nanoTime();

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(EssayGradingPrompts.system(command.criteria())));
        messages.add(UserMessage.builder()
                .text(USER_INSTRUCTION)
                .media(new Media(mimeTypeOf(command.imageUrl()), URI.create(command.imageUrl())))
                .build());

        Set<Long> allowedIds = new LinkedHashSet<>();
        command.criteria().forEach(criterion -> allowedIds.add(criterion.rubricItemId()));

        Map<Long, RubricJudgement> judgements = new LinkedHashMap<>();
        Map<String, Integer> toolStatusCounts = new LinkedHashMap<>();
        String transcription = "";
        EssayGradingStatus pending = EssayGradingStatus.TURN_LIMIT_REACHED;
        int modelCalls = 0;
        int toolCalls = 0;
        int dropped = 0;
        int malformed = 0;
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer reasoningTokens = null;

        for (int call = 1; call <= MAX_MODEL_CALLS; call++) {
            modelCalls = call;
            ChatResponse response = call(new Prompt(messages, options(withTools, seed)), call);
            Generation generation = response.getResult();
            if (generation == null) {
                throw new IllegalStateException("모델이 응답을 하나도 돌려주지 않았다");
            }
            AssistantMessage output = generation.getOutput();
            Usage usage = response.getMetadata().getUsage();
            promptTokens = add(promptTokens, usage == null ? null : usage.getPromptTokens());
            completionTokens = add(completionTokens, usage == null ? null : usage.getCompletionTokens());
            reasoningTokens = add(reasoningTokens, reasoningTokensOf(usage));

            if (output.hasToolCalls()) {
                messages.add(output);
                List<ToolResponse> responses = new ArrayList<>();
                for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                    toolCalls++;
                    String result = execute(toolCall);
                    count(toolStatusCounts, statusOf(result));
                    responses.add(new ToolResponse(toolCall.id(), toolCall.name(), result));
                }
                messages.add(ToolResponseMessage.builder().responses(responses).build());
                continue;
            }

            JsonNode parsed = parse(text(output));
            if (parsed == null) {
                malformed++;
                pending = EssayGradingStatus.MALFORMED_OUTPUT;
                messages.add(output);
                messages.add(new UserMessage(RETRY_JSON));
                continue;
            }

            transcription = parsed.path("transcription").asString("");
            dropped += merge(judgements, parsed.path("items"), allowedIds);

            List<Long> missing = allowedIds.stream().filter(id -> !judgements.containsKey(id)).toList();
            if (missing.isEmpty()) {
                log.info("[서술형] 판정 완료 도구={} 차수={} 도구호출={} 항목={} 버림={}",
                        withTools, call, toolCalls, judgements.size(), dropped);
                return new EssayGradingRun(
                        EssayGradingResult.judged(transcription, List.copyOf(judgements.values())),
                        new Trace(withTools, modelCalls, toolCalls, Map.copyOf(toolStatusCounts),
                                dropped, malformed, promptTokens, completionTokens, reasoningTokens,
                                elapsed(startedAt)));
            }

            pending = EssayGradingStatus.TURN_LIMIT_REACHED;
            messages.add(output);
            messages.add(new UserMessage(EssayGradingPrompts.missingItems(missing)));
        }

        log.info("[서술형] 판정 미완 status={} 도구={} 차수={} 도구호출={} 항목={}/{}",
                pending, withTools, modelCalls, toolCalls, judgements.size(), allowedIds.size());
        return new EssayGradingRun(
                EssayGradingResult.incomplete(pending, transcription, List.copyOf(judgements.values())),
                new Trace(withTools, modelCalls, toolCalls, Map.copyOf(toolStatusCounts),
                        dropped, malformed, promptTokens, completionTokens, reasoningTokens,
                        elapsed(startedAt)));
    }

    /**
     * 이미지 형식. <b>data URI 일 때만 거기 적힌 것을 믿는다</b> — 측정 하네스가 파일을 그대로
     * 실어 보내는 경로다.
     *
     * <p>그 밖에는 PNG 로 둔다. 운영이 넘기는 것은 S3 presigned URL 이라 확장자가 없고, 형식은
     * 쿼리스트링 뒤에 숨어 있지 않다. 업로드는 PNG·JPEG 둘 다 받으므로
     * <b>JPEG 필기가 PNG 로 선언돼 나가는 자리가 여기다</b> — 형식을 칸에서 들고 오지 않는 한
     * 여기서는 알 수 없다.
     */
    private static MimeType mimeTypeOf(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("data:")) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        int separator = imageUrl.indexOf(';');
        if (separator < 0) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        String declared = imageUrl.substring("data:".length(), separator);
        return declared.isBlank() ? MimeTypeUtils.IMAGE_PNG : MimeTypeUtils.parseMimeType(declared);
    }

    /**
     * 모델을 부르고, 실패하면 <b>귀속에 필요한 것만</b> 남기고 그대로 올린다.
     *
     * <p>남기는 것은 HTTP 상태와 OpenAI 가 붙인 {@code type}·{@code code} 뿐이다. 이 셋이면
     * 인증 실패·정원 초과·요청 거절이 갈린다. <b>{@code message} 와 {@code body} 는 남기지
     * 않는다</b> — 거절 사유에 우리가 보낸 프롬프트 조각이 실려 오고, 그 조각이 곧 학생 답안이다.
     *
     * <p>여기서 잡는 이유는 {@code com.openai} 를 아는 계층이 여기라서다. 도메인이 SDK 예외를
     * 열어 보게 하면 SDK 를 갈아끼울 때 도메인까지 열어야 한다.
     */
    private ChatResponse call(Prompt prompt, int modelCall) {
        try {
            return chatModel.call(prompt);
        } catch (OpenAIServiceException exception) {
            log.warn("[서술형] 모델 호출 실패 차수={} status={} type={} code={}",
                    modelCall, exception.statusCode(),
                    exception.type().orElse("-"), exception.code().orElse("-"));
            throw exception;
        }
    }

    /**
     * 도구를 빼고 부르면 그것이 B 군이다. 프롬프트의 도구 문단은 그대로 둔다 — 프롬프트까지 바꾸면
     * 두 군의 차이가 도구 유무 하나로 남지 않는다(금지 16).
     */
    private OpenAiChatOptions options(boolean withTools, Integer seed) {
        OpenAiChatOptions.Builder builder = baseOptions.mutate()
                .toolCallbacks(withTools ? toolCallbacks : List.of());
        if (seed != null) {
            builder.seed(seed);
        }
        return builder.build();
    }

    /**
     * 도구가 던지면 루프를 죽이지 않고 그 사실을 모델에 돌려준다. 인자를 만든 것이 모델이라
     * 실패도 모델이 고칠 수 있는 종류다.
     */
    private String execute(AssistantMessage.ToolCall toolCall) {
        ToolCallback callback = toolsByName.get(toolCall.name());
        if (callback == null) {
            return "그런 도구는 없다. 쓸 수 있는 것은 " + String.join(", ", toolsByName.keySet()) + " 뿐이다.";
        }
        try {
            return callback.call(toolCall.arguments() == null ? "" : toolCall.arguments());
        } catch (RuntimeException exception) {
            // 인자는 남기지 않는다. 학생 답안 조각이다.
            log.warn("[도구] 실행 실패 tool={}", toolCall.name());
            return "도구 실행이 실패했다. 인자를 확인하고 다시 시도하라.";
        }
    }

    /**
     * 우리가 준 목록에 없는 {@code rubricItemId} 는 버린다.
     *
     * <p>모델이 만들어 낸 id 를 저장하면 존재하지 않는 기준에 판정이 붙고, 점수 분모와 맞지 않는
     * 분자가 생긴다. 같은 id 가 두 번 오면 <b>먼저 온 것을 남긴다</b> — 뒤에 온 것을 채택하면
     * 모델이 스스로 뒤집은 판정을 근거 없이 신뢰하는 셈이다.
     *
     * @return 버린 판정 수
     */
    private int merge(Map<Long, RubricJudgement> judgements, JsonNode items, Set<Long> allowedIds) {
        int dropped = 0;
        for (JsonNode item : items) {
            long rubricItemId = item.path("rubricItemId").asLong(-1);
            RubricVerdict verdict = verdictOf(item.path("verdict").asString(""));
            if (verdict == null || !allowedIds.contains(rubricItemId)) {
                dropped++;
                continue;
            }
            judgements.putIfAbsent(rubricItemId,
                    new RubricJudgement(rubricItemId, verdict, item.path("evidence").asString("")));
        }
        return dropped;
    }

    private static RubricVerdict verdictOf(String raw) {
        for (RubricVerdict verdict : RubricVerdict.values()) {
            if (verdict.name().equals(raw)) {
                return verdict;
            }
        }
        return null;
    }

    /**
     * 평문에서 JSON 객체를 꺼낸다. 코드 펜스만 벗기고 그 밖의 교정은 하지 않는다 — 응답을 억지로
     * 살리려 들면 모델이 실제로 무엇을 냈는지 알 수 없게 된다.
     *
     * @return 읽지 못했으면 {@code null}
     */
    private JsonNode parse(String text) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(text));
            return root != null && root.isObject() ? root : null;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private static String stripCodeFence(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstNewline + 1);
        int closing = body.lastIndexOf("```");
        return closing < 0 ? body.strip() : body.substring(0, closing).strip();
    }

    /**
     * 도구 결과에서 상태와 사유만 꺼낸다. 값·근거는 학생 답안 조각이라 세지 않는다.
     *
     * <p>사유가 있으면 {@code UNREADABLE:IMPLICIT_MULT} 처럼 붙여 하나의 키로 센다 — 단계 4 가
     * 재려는 D9 는 "{@code UNREADABLE} 중 곱셈 기호 누락 비율" 이라, 상태만 세면 분자를 못 낸다.
     */
    private String statusOf(String result) {
        try {
            JsonNode root = objectMapper.readTree(result);
            if (root == null || !root.isObject()) {
                return "UNKNOWN";
            }
            String status = root.path("status").asString("UNKNOWN");
            JsonNode reason = root.path("reason");
            return reason.isMissingNode() || reason.isNull()
                    ? status
                    : status + ":" + reason.asString("UNKNOWN");
        } catch (JacksonException exception) {
            return "UNKNOWN";
        }
    }

    private static void count(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    /**
     * 추론 토큰. Spring AI 의 공통 {@code Usage} 에는 자리가 없어 SDK 원본에서 꺼낸다.
     *
     * <p><b>완성 토큰에 이미 포함된 값이다.</b> 따로 더하면 비용이 두 번 세어진다 — 여기서는
     * "완성 토큰 중 얼마가 추론이었나" 를 보려고 따로 센다. 모델이 안 내려주면 {@code null} 이다.
     */
    private static Integer reasoningTokensOf(Usage usage) {
        if (usage == null || !(usage.getNativeUsage() instanceof CompletionUsage native_)) {
            return null;
        }
        return native_.completionTokensDetails()
                .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
                .map(Long::intValue)
                .orElse(null);
    }

    private static Integer add(Integer left, Integer right) {
        if (right == null) {
            return left;
        }
        return left == null ? right : left + right;
    }

    private static long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static String text(AssistantMessage output) {
        String text = output.getText();
        return text == null ? "" : text;
    }
}
