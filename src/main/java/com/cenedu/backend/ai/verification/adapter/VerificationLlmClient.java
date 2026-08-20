package com.cenedu.backend.ai.verification.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmUseCase;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 검증에 필요한 LLM 호출 셋. Solver·루브릭 심사·자산 심사는 <b>서로 다른 호출</b>이다.
 *
 * <p>한 호출로 합치지 않는다. Solver 는 정답 없이 풀어야 하고 나머지 둘은 정답을 봐야 한다.
 * 같은 컨텍스트에 두면 Solver 가 정답을 보게 되고, 그 순간 이 검증기의 존재 이유가 사라진다.
 *
 * <p>모델은 {@link LlmUseCase#VERIFICATION} 이다 — 저작측과 같은 모델이면 같은 방식으로 틀리고,
 * 둘 다 틀려도 대조는 통과한다.
 *
 * <p>seed 를 고정한다. 판정 결과가 호출마다 흔들리면 재검증이 다른 답을 내고, 어느 쪽이 맞는지
 * 알 방법이 없다. 다만 보장이 아니라 best-effort 다.
 */
@Component
public class VerificationLlmClient {

    /** 판정 재현성을 위한 고정 시드. 값 자체에 의미는 없다. */
    private static final long VERIFICATION_SEED = 20260818L;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public VerificationLlmClient(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /** Blind 문항을 혼자 풀게 한다. */
    public SolverAnswer solve(BlindQuestion blind) {
        String blindJson = writeBlind(blind);
        JsonNode root = call(
                VerificationPrompts.solverSystemPrompt(),
                VerificationPrompts.solverUserPrompt(blindJson),
                VerificationStructuredOutputSchemas.SOLVER);

        boolean solved = root.path("solved").asBoolean(false);
        String reason = root.path("reason").asString("");
        if (!solved) {
            return SolverAnswer.unsolved(reason);
        }

        JsonNode answers = root.path("answers");
        if (!answers.isArray() || answers.isEmpty()) {
            throw new SolverResponseParseException("Solver 응답의 answers 가 배열이 아니거나 비었습니다.");
        }
        Map<String, String> byUnitKey = new LinkedHashMap<>();
        for (JsonNode answer : answers) {
            String unitKey = answer.path("unitKey").asString(null);
            String value = answer.path("answer").asString(null);
            if (unitKey == null || unitKey.isBlank() || value == null) {
                throw new SolverResponseParseException(
                        "Solver 응답의 answers 원소에 unitKey 또는 answer 가 없습니다.");
            }
            byUnitKey.put(unitKey, value);
        }
        return new SolverAnswer(true, byUnitKey, reason);
    }

    /**
     * 정답이 든 원본으로 결함을 찾는다. 해설 정합 · learningGuide 누출 · (ESSAY 면) 루브릭을
     * <b>한 번의 호출</b>로 본다.
     *
     * @param includeRubric ESSAY 일 때 true. 루브릭 절을 요구한다
     * @return 결함 목록. 비어 있으면 결함 없음이다
     */
    public List<OriginalDefect> inspectOriginal(
            QuestionSnapshotV1 snapshot,
            boolean includeRubric,
            CurriculumScope expectedCurriculum
    ) {
        JsonNode root = call(
                VerificationPrompts.contentIntegritySystemPrompt(includeRubric),
                VerificationPrompts.contentIntegrityUserPrompt(snapshot, expectedCurriculum),
                VerificationStructuredOutputSchemas.ORIGINAL);

        JsonNode findings = root.path("findings");
        if (findings.isMissingNode() || findings.isNull()) {
            throw new SolverResponseParseException("원본 검사 응답에 findings 가 없습니다.");
        }
        if (!findings.isArray()) {
            throw new SolverResponseParseException("원본 검사 응답의 findings 가 배열이 아닙니다.");
        }

        List<OriginalDefect> defects = new ArrayList<>();
        for (JsonNode finding : findings) {
            String type = finding.path("type").asString("").trim().toUpperCase();
            if (type.isEmpty()) {
                throw new SolverResponseParseException("원본 검사 결함에 type 이 없습니다.");
            }
            defects.add(new OriginalDefect(
                    type,
                    finding.path("kind").asString("").trim().toUpperCase(),
                    finding.path("location").asString("").trim(),
                    finding.path("detail").asString("").trim()));
        }
        return defects;
    }

    /** 서술형 채점 기준의 의미를 심사한다. 구조 검사는 저작측 Validator 가 이미 했다. */
    public RubricJudgement judgeRubric(QuestionSnapshotV1 snapshot) {
        JsonNode root = call(
                VerificationPrompts.rubricSystemPrompt(),
                VerificationPrompts.rubricUserPrompt(snapshot),
                VerificationStructuredOutputSchemas.RUBRIC);
        return new RubricJudgement(
                root.path("axis").asString("").trim(),
                root.path("detail").asString("").trim());
    }

    /** altText 와 본문 정합을 심사한다. Blind 가 아니라 원본을 본다. */
    public AssetJudgement judgeAsset(QuestionSnapshotV1 snapshot) {
        JsonNode root = call(
                VerificationPrompts.assetSystemPrompt(),
                VerificationPrompts.assetUserPrompt(snapshot),
                VerificationStructuredOutputSchemas.ASSET);
        return new AssetJudgement(
                root.path("issue").asString("").trim(),
                root.path("detail").asString("").trim());
    }

    private JsonNode call(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, null);
    }

    private JsonNode call(String systemPrompt, String userPrompt, String schema) {
        String text = llmClient.completeStructured(
                systemPrompt, List.of(ChatMessage.user(userPrompt)), VERIFICATION_SEED,
                LlmUseCase.VERIFICATION, schema).text();
        return parse(text);
    }

    /**
     * 평문에서 JSON 을 꺼낸다. {@code LlmClient} 는 구조화 출력 경로가 아니라 String 만 돌려준다.
     *
     * <p>코드 펜스를 벗겨 낸다. 프롬프트로 금지해도 붙어 오는 경우가 있고, 그때마다 판정 전체가
     * ERROR 가 되면 검증이 형식 사고로 멈춘다. 다만 그 밖의 교정은 하지 않는다 — 응답을 억지로
     * 살리려 들면 모델이 실제로 무엇을 냈는지 알 수 없게 된다.
     */
    private JsonNode parse(String text) {
        String stripped = stripCodeFence(text);
        try {
            JsonNode root = objectMapper.readTree(stripped);
            if (root == null || !root.isObject()) {
                throw new SolverResponseParseException("응답이 JSON 객체가 아닙니다.");
            }
            return root;
        } catch (JacksonException e) {
            throw new SolverResponseParseException("응답을 JSON 으로 읽지 못했습니다.", e);
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

    private String writeBlind(BlindQuestion blind) {
        try {
            return objectMapper.writeValueAsString(blind);
        } catch (JacksonException e) {
            // Blind 는 우리가 만든 record 다. 여기서 실패하면 입력이 아니라 코드 문제다.
            throw new IllegalStateException("Blind 문항을 직렬화하지 못했습니다.", e);
        }
    }

    /** 루브릭 심사 결과. {@code axis} 가 비어 있으면 문제 없음이다. */
    public record RubricJudgement(String axis, String detail) {

        boolean hasIssue() {
            return axis != null && !axis.isBlank();
        }
    }

    /** 자산 심사 결과. {@code issue} 가 비어 있으면 문제 없음이다. */
    public record AssetJudgement(String issue, String detail) {

        boolean hasIssue() {
            return issue != null && !issue.isBlank();
        }
    }
}
