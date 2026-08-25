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
 * <p>altText는 자산에 표시된 좌표·식·수치·보기를 대신 전달하는 접근성 정보다. 정답과 같은 값이
 * 포함됐는지는 검사하지 않고, 발문이 요구하는 자산과 다른 내용을 설명하는지만 판정한다.
 */
@Component
public class AssetChecks {

    /** 프롬프트가 낼 수 있는 문제 유형. */
    private static final Set<String> IGNORED_LEGACY_ISSUES = Set.of(
            "LEAK", "UNNECESSARY", "TEXT_DUPLICATION", "GENERIC_REFERENCE");

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
                EvidencePrefix.of(EvidencePrefix.MANIFEST, String.join(", ", notReady)));
    }

    /** Snapshot·계획·artifact의 key와 구조화 SVG 메타데이터를 검증한다. */
    public VerificationFinding assetIntegrity(QuestionSnapshotV1 snapshot, DraftAssetManifest manifest) {
        var assets = snapshot == null || snapshot.assets() == null ? List.<String>of()
                : snapshot.assets().stream().map(asset -> asset.assetKey()).toList();
        var plans = manifest == null || manifest.plans() == null ? List.<String>of()
                : manifest.plans().stream().map(plan -> plan.assetKey()).toList();
        var artifacts = manifest == null || manifest.artifacts() == null ? List.<DraftAssetArtifact>of()
                : manifest.artifacts();
        if (!assets.equals(plans) || assets.size() != artifacts.size()) {
            return Findings.fail(VerificationCheckType.ASSET_CONSISTENCY,
                    VerificationIssueCode.ASSET_INCONSISTENT, "Snapshot과 자산 manifest의 key가 다릅니다.",
                    EvidencePrefix.of(EvidencePrefix.MANIFEST, "KEY_MISMATCH"));
        }
        for (DraftAssetArtifact artifact : artifacts) {
            if (artifact == null || artifact.status() != DraftAssetStatus.READY
                    || artifact.checksum() == null || artifact.checksum().isBlank()
                    || artifact.widthPx() == null || artifact.widthPx() <= 0
                    || artifact.heightPx() == null || artifact.heightPx() <= 0) {
                return Findings.fail(VerificationCheckType.ASSET_CONSISTENCY,
                        VerificationIssueCode.ASSET_INCONSISTENT, "자산 무결성 메타데이터가 부족합니다.",
                        EvidencePrefix.of(EvidencePrefix.MANIFEST, "INVALID_METADATA"));
            }
        }
        return Findings.pass(VerificationCheckType.ASSET_CONSISTENCY, "자산 key와 무결성 메타데이터가 일치합니다.");
    }

    /** altText가 비어 있지 않고 발문이 요구하는 자산과 어긋나지 않는지 본다. */
    public VerificationFinding altTextIntegrity(QuestionSnapshotV1 snapshot) {
        if (snapshot.assets() == null || snapshot.assets().isEmpty()) {
            return Findings.notApplicable(VerificationCheckType.ASSET_CONSISTENCY,
                    "문항에 그림이 없습니다.");
        }

        for (var asset : snapshot.assets()) {
            if (asset == null || asset.altText() == null || asset.altText().isBlank()
                    || asset.altText().strip().equalsIgnoreCase(asset.assetKey())) {
                return Findings.fail(VerificationCheckType.ASSET_CONSISTENCY,
                        VerificationIssueCode.ASSET_IMAGE_REGENERATABLE,
                        "그림 설명에 학생이 확인할 수 있는 구체적인 정보가 없습니다.",
                        EvidencePrefix.of(EvidencePrefix.ALTTEXT, "GENERIC_OR_BLANK"));
            }
        }

        VerificationLlmClient.AssetJudgement judgement = llmClient.judgeAsset(snapshot);
        if (!judgement.hasIssue()) {
            return Findings.pass(VerificationCheckType.ASSET_CONSISTENCY,
                    "그림 설명이 발문과 일치합니다.");
        }

        String issue = judgement.issue().toUpperCase();
        if (IGNORED_LEGACY_ISSUES.contains(issue)) {
            return Findings.pass(VerificationCheckType.ASSET_CONSISTENCY,
                    "그림에 표시된 좌표·식·수치·보기 정보는 altText에 포함할 수 있습니다.");
        }
        if (!issue.equals("MISMATCH")) {
            return Findings.error(VerificationCheckType.ASSET_CONSISTENCY,
                    "자산 심사 응답의 문제 유형을 알 수 없습니다.", "issue=" + judgement.issue());
        }
        return Findings.fail(
                VerificationCheckType.ASSET_CONSISTENCY,
                VerificationIssueCode.ASSET_IMAGE_REGENERATABLE,
                "그림 설명이 발문과 어긋납니다.",
                EvidencePrefix.of(EvidencePrefix.ALTTEXT, issue, judgement.detail()));
    }
}
