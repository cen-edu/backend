package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProblemSearchCorpusEligibilityService {
    /** 현재 Snapshot과 자산 저장 상태를 검색 corpus 편입 가능성으로 판정한다. */
    public SearchCorpusEligibility evaluate(QuestionSnapshotV1 snapshot, Map<String, String> assetStorageKeys) {
        if (snapshot == null || snapshot.metadata() == null) return SearchCorpusEligibility.REJECTED;
        if (snapshot.assets() == null) return SearchCorpusEligibility.REJECTED;
        for (SnapshotAssetReference asset : snapshot.assets()) {
            if (asset == null || asset.assetKey() == null || assetStorageKeys == null
                    || !assetStorageKeys.containsKey(asset.assetKey())
                    || assetStorageKeys.get(asset.assetKey()) == null) {
                return SearchCorpusEligibility.WAITING_FOR_ASSETS;
            }
        }
        return SearchCorpusEligibility.READY;
    }
}
