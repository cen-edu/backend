package com.cenedu.backend.ai.verification.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetArtifact;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetStatus;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationExpectation;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;

import org.springframework.stereotype.Component;

/**
 * {@code ASSET} 범위 판정. manifest 준비 상태(코드)와 altText·본문 정합(LLM)을 본다.
 *
 * <p>두 판정을 하나의 {@code ASSET_CONSISTENCY} Finding 으로 합치지 않는다. 준비되지 않은 자산과
 * 정답이 새는 altText 는 조율측의 대응이 다르다 — 앞은 기다리거나 재생성이고 뒤는 문항 수정이다.
 * 다만 계약의 CheckType 이 하나뿐이라 둘 다 {@code ASSET_CONSISTENCY} 로 나가며, 어느 쪽인지는
 * message 로 구분한다. CheckType 을 늘리는 것은 계약 변경이므로 여기서 하지 않는다.
 */
@Component
public class AssetChecks {

    /** 프롬프트가 낼 수 있는 문제 유형. */
    private static final Set<String> ISSUES = Set.of("LEAK", "MISMATCH");

    private final VerificationLlmClient llmClient;

    public AssetChecks(VerificationLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /** 필요한 자산이 전부 {@code READY} 인가. */
    public VerificationFinding manifestReadiness(
            VerificationExpectation expectation, DraftAssetManifest manifest
    ) {
        List<String> requiredKeys = expectation == null ? null : expectation.requiredAssetKeys();
        if (requiredKeys == null || requiredKeys.isEmpty()) {
            return Findings.notApplicable(VerificationCheckType.ASSET_CONSISTENCY,
                    "필요한 자산이 지정되지 않았습니다.");
        }

        Map<String, DraftAssetStatus> statusByKey = new HashMap<>();
        if (manifest != null && manifest.artifacts() != null) {
            for (DraftAssetArtifact artifact : manifest.artifacts()) {
                if (artifact != null && artifact.assetKey() != null) {
                    statusByKey.put(artifact.assetKey(), artifact.status());
                }
            }
        }

        List<String> notReady = new ArrayList<>();
        for (String assetKey : requiredKeys) {
            DraftAssetStatus status = statusByKey.get(assetKey);
            if (status != DraftAssetStatus.READY) {
                notReady.add(assetKey + "=" + (status == null ? "없음" : status.name()));
            }
        }

        if (notReady.isEmpty()) {
            return Findings.pass(VerificationCheckType.ASSET_CONSISTENCY,
                    "필요한 자산이 모두 준비되었습니다.");
        }
        return Findings.fail(
                VerificationCheckType.ASSET_CONSISTENCY,
                VerificationIssueCode.ASSET_INCONSISTENT,
                "준비되지 않은 자산이 " + notReady.size() + "건 있습니다.",
                String.join(", ", notReady));
    }

    /**
     * altText 에 정답이 새었는지, 발문과 어긋나는지 본다.
     *
     * <p>이 판정은 <b>Blind 가 아니라 원본</b>을 입력으로 한다. Blind 에는 정답이 없어서
     * "altText 에 정답이 새어 있는지"를 판정할 수 없다. Solver 호출과 분리된 별개의 호출이다.
     */
    public VerificationFinding altTextIntegrity(QuestionSnapshotV1 snapshot) {
        if (snapshot.assets() == null || snapshot.assets().isEmpty()) {
            return Findings.notApplicable(VerificationCheckType.ASSET_CONSISTENCY,
                    "문항에 그림이 없습니다.");
        }

        VerificationLlmClient.AssetJudgement judgement = llmClient.judgeAsset(snapshot);
        if (!judgement.hasIssue()) {
            return Findings.pass(VerificationCheckType.ASSET_CONSISTENCY,
                    "그림 설명이 보이는 정보만 담고 발문과 일치합니다.");
        }

        String issue = judgement.issue().toUpperCase();
        if (!ISSUES.contains(issue)) {
            return Findings.error(VerificationCheckType.ASSET_CONSISTENCY,
                    "자산 심사 응답의 문제 유형을 알 수 없습니다.", "issue=" + judgement.issue());
        }
        return Findings.fail(
                VerificationCheckType.ASSET_CONSISTENCY,
                VerificationIssueCode.ASSET_INCONSISTENT,
                issue.equals("LEAK")
                        ? "그림 설명에 그림에 보이지 않는 정보가 있습니다."
                        : "그림 설명이 발문과 어긋납니다.",
                issue + ": " + judgement.detail());
    }
}
