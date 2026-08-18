package com.cenedu.backend.ai.verification.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 정답 유출 방지가 <b>걸려야 할 때 걸리고 안 걸려야 할 때 안 걸리는지</b> 양쪽을 본다.
 *
 * <p>한쪽만 보면 규칙을 신뢰할 수 없다. 전부 뭉개는 구현도 "유출을 막는다"는 테스트는 통과하고,
 * 아무것도 안 하는 구현도 "정상 근거를 보존한다"는 테스트는 통과한다.
 */
class FindingSanitizerTest {

    private final FindingSanitizer sanitizer = new FindingSanitizer();

    /** answerRaw 가 24 인 스냅샷. 짧은 숫자 정답이 위치 표현과 충돌하기 쉬운 최악의 경우다. */
    private static QuestionSnapshotV1 shortNumericAnswer() {
        return VerificationFixtures.withAnswerRaw(VerificationFixtures.shortInputSnapshot(), "24");
    }

    @Test
    @DisplayName("위치 표현 안의 숫자는 정답과 같아도 뭉개지 않는다")
    void fieldPathIsNotRedacted() {
        String evidence = "STRUCTURE: answerUnits[24] 의 compareMethod 가 VALUE 인데 "
                + "answerRaw 가 비어 있습니다.";

        FindingSanitizer.Result result = sanitizer.sanitize(
                List.of(fail(evidence)), shortNumericAnswer());

        assertThat(result.findings().getFirst().evidence())
                .as("위치 표현이 뭉개지면 조율측이 왜 실패했는지 알 수 없다")
                .isEqualTo(evidence);
        assertThat(result.redacted()).isEmpty();
    }

    @Test
    @DisplayName("값으로 등장한 정답은 뭉갠다")
    void answerValueIsRedacted() {
        FindingSanitizer.Result result = sanitizer.sanitize(
                List.of(fail("LEAKAGE: 정답 24 를 learningGuide 가 노출합니다.")),
                shortNumericAnswer());

        assertThat(result.findings().getFirst().evidence()).isEqualTo(FindingSanitizer.REDACTED);
        assertThat(result.redacted()).containsExactly(VerificationCheckType.ANSWER_CONSISTENCY);
    }

    @Test
    @DisplayName("한국어 조사가 붙어도 걸린다 — 24개 처럼 붙여 쓰는 경우")
    void answerValueWithKoreanParticleIsRedacted() {
        FindingSanitizer.Result result = sanitizer.sanitize(
                List.of(fail("LEAKAGE: 개념 안내가 24개라고 적었습니다.")), shortNumericAnswer());

        assertThat(result.findings().getFirst().evidence()).isEqualTo(FindingSanitizer.REDACTED);
    }

    @Test
    @DisplayName("기호로 끝나는 LaTeX 정답도 걸린다")
    void latexAnswerIsRedacted() {
        QuestionSnapshotV1 snapshot = VerificationFixtures.withAnswerRaw(
                VerificationFixtures.shortInputSnapshot(), "\\frac{144}{6}");

        FindingSanitizer.Result result = sanitizer.sanitize(
                List.of(fail("LEAKAGE: 개념 안내가 \\frac{144}{6} 을 담고 있습니다.")), snapshot);

        assertThat(result.findings().getFirst().evidence()).isEqualTo(FindingSanitizer.REDACTED);
    }

    @Test
    @DisplayName("더 긴 숫자의 일부와 같아도 뭉개지 않는다")
    void substringOfLongerNumberIsNotRedacted() {
        String evidence = "STRUCTURE: 총 240 문항 중 정합성 위반이 있습니다.";

        FindingSanitizer.Result result = sanitizer.sanitize(
                List.of(fail(evidence)), shortNumericAnswer());

        assertThat(result.findings().getFirst().evidence()).isEqualTo(evidence);
    }

    @Test
    @DisplayName("message 에 들어온 정답도 뭉갠다")
    void answerInMessageIsRedacted() {
        VerificationFinding finding = new VerificationFinding(
                VerificationCheckType.ANSWER_CONSISTENCY,
                VerificationFindingStatus.FAIL,
                VerificationSeverity.ERROR,
                VerificationIssueCode.ANSWER_INCONSISTENT,
                "해설이 정답 24 와 다른 값을 씁니다.",
                "EXPLANATION: explanation");

        FindingSanitizer.Result result =
                sanitizer.sanitize(List.of(finding), shortNumericAnswer());

        assertThat(result.findings().getFirst().message()).isEqualTo(FindingSanitizer.REDACTED);
    }

    @Test
    @DisplayName("합의된 상한을 넘으면 자르고 잘린 사실을 표시한다")
    void tooLongTextIsTruncated() {
        String longEvidence = "STRUCTURE: " + "가".repeat(1_200);

        FindingSanitizer.Result result = sanitizer.sanitize(
                List.of(fail(longEvidence)), VerificationFixtures.shortInputSnapshot());

        assertThat(result.findings().getFirst().evidence())
                .hasSize(1_000)
                .endsWith("…");
    }

    @Test
    @DisplayName("정답이 없는 스냅샷에서는 아무것도 뭉개지 않는다")
    void noSecretsMeansNoRedaction() {
        String evidence = "OVERLAPPING: R2 와 R3 이 모두 소인수분해 수행을 요구합니다.";

        FindingSanitizer.Result result = sanitizer.sanitize(
                List.of(fail(evidence)), VerificationFixtures.essaySnapshot());

        assertThat(result.findings().getFirst().evidence()).isEqualTo(evidence);
        assertThat(result.redacted()).isEmpty();
    }

    private static VerificationFinding fail(String evidence) {
        return new VerificationFinding(
                VerificationCheckType.ANSWER_CONSISTENCY,
                VerificationFindingStatus.FAIL,
                VerificationSeverity.ERROR,
                VerificationIssueCode.ANSWER_INCONSISTENT,
                "구조·참조 정합 위반이 1건 있습니다.",
                evidence);
    }
}
