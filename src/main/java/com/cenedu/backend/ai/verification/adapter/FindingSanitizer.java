package com.cenedu.backend.ai.verification.adapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;

import org.springframework.stereotype.Component;

/**
 * Finding 의 {@code message} · {@code evidence} 에서 정답을 걸러내고 길이를 자른다.
 *
 * <p><b>누출 검사 결과가 그 자체로 누출이 되는 함정을 막는다.</b> 보고서는 저장되고 로그에 남을 수
 * 있으며 조율측이 교사 화면에 노출할 수도 있다. "learningGuide 가 정답 420 을 노출합니다"는
 * 근거가 정답을 담은 셈이다.
 *
 * <p>프롬프트로도 같은 지시를 하지만 <b>LLM 은 지시를 어긴다</b>는 전제로 두 겹을 둔다. 이쪽이
 * 두 번째 겹이며, 첫 번째 겹인 Blind 화이트리스트가 막지 못하는 경로(원본 검사)를 담당한다.
 *
 * <h2>부분 문자열이 아니라 단어 경계로 본다</h2>
 * 부분 문자열로 검사하면 정상 Finding 이 통째로 뭉개진다. {@code answerRaw} 가 {@code "24"} 인
 * STEP_FILL 을 보면, Validator 위반 메시지에 {@code answerUnits[24]} · {@code steps[2]} 같은
 * 위치 표현이 들어가고 그 안에 정답 문자열이 박힌다. 걸리는 순간 조율측은 왜 실패했는지 알 수
 * 없고, 재생성해도 같은 자리에서 또 뭉개진다. <b>이건 오탐이 아니라 기능 파괴다.</b>
 *
 * <p>그래서 두 단계로 본다.
 * <ol>
 *   <li>필드 경로({@code answerUnits[4]} · {@code learningGuide.keyPoints[2]} ·
 *       {@code step(ST1).segments[0]})를 자리표시자로 치환한다</li>
 *   <li>남은 텍스트에서 <b>단어 경계</b>로 정답을 찾는다</li>
 * </ol>
 * 이러면 {@code "24"} 가 {@code answerUnits[24]} 에 있을 땐 통과하고
 * {@code "정답은 24입니다"} 에 있을 땐 걸린다. <b>위치 표현과 값 표현을 가르는 것</b>이
 * 이 규칙의 본질이다.
 *
 * <p>길이 하한은 두지 않는다. 경계 매칭이 그 역할을 대신하며, 하한을 두면 객관식 정답처럼
 * 짧은 키가 그물을 빠져나간다.
 *
 * <p>객관식 {@code choiceKey} 는 애매하게 남는다. {@code evidence} 에 값으로 등장할 개연성이
 * 낮고 등장하더라도 {@code choiceKey C1} 형태의 위치 표현이며, 객관식 정답 노출은 이 방어선이
 * 아니라 Blind 화이트리스트가 막는다. 여기서 완벽을 노리면 정상 동작을 깎는다.
 */
@Component
public class FindingSanitizer {

    /** 합의된 상한. */
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int MAX_EVIDENCE_LENGTH = 1_000;

    static final String REDACTED = "근거에 정답이 포함되어 제거되었습니다.";

    /** 필드 경로 자리표시자. 정답 문자열과 겹치지 않는 문자를 쓴다. */
    private static final String PATH_PLACEHOLDER = "§PATH§";

    /** {@code name[0]} · {@code a.b.c} · {@code name(KEY)} 형태의 위치 표현. */
    private static final Pattern FIELD_PATH = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*|\\[[0-9]+\\]|\\([A-Za-z0-9_]+\\))+");

    /**
     * 경계 판정에 한글을 넣지 않는다. {@code \\b} 를 쓰지 않는 이유도 같다 — 정답이
     * {@code \\frac{144}{6}} 처럼 기호로 끝나면 {@code \\b} 가 성립하지 않아 실제 누출이 통과한다.
     * ASCII 영숫자만 경계로 보면 {@code "24개"} 같은 한국어 문장 안의 값도 걸린다.
     */
    private static final String BOUNDARY_BEFORE = "(?<![A-Za-z0-9_])";
    private static final String BOUNDARY_AFTER = "(?![A-Za-z0-9_])";

    public Result sanitize(List<VerificationFinding> findings, QuestionSnapshotV1 snapshot) {
        Set<String> secrets = secretsOf(snapshot);
        List<VerificationFinding> sanitized = new ArrayList<>(findings.size());
        List<VerificationCheckType> redacted = new ArrayList<>();

        for (VerificationFinding finding : findings) {
            String message = clean(finding.message(), secrets);
            String evidence = clean(finding.evidence(), secrets);
            if (!Objects.equals(message, finding.message())
                    || !Objects.equals(evidence, finding.evidence())) {
                redacted.add(finding.checkType());
            }
            sanitized.add(new VerificationFinding(
                    finding.checkType(),
                    finding.status(),
                    finding.severity(),
                    finding.code(),
                    truncate(message, MAX_MESSAGE_LENGTH),
                    truncate(evidence, MAX_EVIDENCE_LENGTH)));
        }
        return new Result(List.copyOf(sanitized), List.copyOf(redacted));
    }

    /**
     * 정답이 값으로 등장하면 문구 전체를 고정 텍스트로 바꾼다.
     *
     * <p>예외를 던지지 않는다. 검증 결과 전체가 날아가는 것보다 근거 하나가 뭉개지는 편이 낫다.
     */
    private String clean(String text, Set<String> secrets) {
        if (text == null || text.isBlank() || secrets.isEmpty()) {
            return text;
        }
        String masked = FIELD_PATH.matcher(text).replaceAll(PATH_PLACEHOLDER);
        for (String secret : secrets) {
            Pattern pattern = Pattern.compile(
                    BOUNDARY_BEFORE + Pattern.quote(secret) + BOUNDARY_AFTER);
            if (pattern.matcher(masked).find()) {
                return REDACTED;
            }
        }
        return text;
    }

    private static String truncate(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit - 1) + "…";
    }

    /** 스냅샷이 담고 있는 정답 문자열. 공백 값은 경계 매칭이 무의미하므로 뺀다. */
    private static Set<String> secretsOf(QuestionSnapshotV1 snapshot) {
        if (snapshot == null || snapshot.answerUnits() == null) {
            return Set.of();
        }
        Set<String> secrets = new LinkedHashSet<>();
        for (SnapshotAnswerUnit unit : snapshot.answerUnits()) {
            if (unit == null) {
                continue;
            }
            addIfPresent(secrets, unit.answerRaw());
            addIfPresent(secrets, unit.answerNormalized());
        }
        return secrets;
    }

    private static void addIfPresent(Set<String> secrets, String value) {
        if (value != null && !value.isBlank()) {
            secrets.add(value.strip());
        }
    }

    /**
     * @param findings 정제된 Finding
     * @param redacted 정답이 걸려 뭉갠 Finding 의 CheckType. <b>값은 담지 않는다</b>
     */
    public record Result(
            List<VerificationFinding> findings, List<VerificationCheckType> redacted
    ) {
    }
}
