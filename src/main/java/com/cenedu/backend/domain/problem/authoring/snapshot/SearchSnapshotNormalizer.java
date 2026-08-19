package com.cenedu.backend.domain.problem.authoring.snapshot;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 검색 직전에 답안과 의미를 건드리지 않고 표현 구조만 정규화한다. */
@Component
public class SearchSnapshotNormalizer {
    /** 사용하지 않는 표현 필드를 제거하고 그림 설명의 안전한 기본값을 채운다. */
    public QuestionSnapshotV1 normalize(QuestionSnapshotV1 snapshot) {
        if (snapshot == null) return null;
        List<SnapshotContentBlock> blocks = snapshot.contentBlocks() == null ? List.of()
                : snapshot.contentBlocks().stream().map(this::normalizeBlock).toList();
        Set<String> referencedAssets = blocks.stream()
                .map(SnapshotContentBlock::assetRef).filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<SnapshotAssetReference> assets = snapshot.assets() == null ? List.of()
                : snapshot.assets().stream().filter(asset -> referencedAssets.contains(asset.assetKey()))
                .map(asset -> new SnapshotAssetReference(asset.assetKey(),
                        asset.altText() == null || asset.altText().isBlank()
                                ? "문제 그림 " + asset.assetKey() : asset.altText()))
                .toList();
        return new QuestionSnapshotV1(snapshot.schemaVersion(), snapshot.metadata(), blocks, assets,
                snapshot.choices(), snapshot.steps(), snapshot.answerUnits(), snapshot.explanation(),
                snapshot.learningGuide(), snapshot.rubricItems());
    }

    private SnapshotContentBlock normalizeBlock(SnapshotContentBlock block) {
        if (block == null) return null;
        return switch (block.blockKind()) {
            case TEXT -> new SnapshotContentBlock(block.blockKey(), block.blockKind(), block.displayOrder(),
                    block.text(), null, null);
            case FIGURE -> new SnapshotContentBlock(block.blockKey(), block.blockKind(), block.displayOrder(),
                    null, block.assetRef(), null);
            case TABLE -> new SnapshotContentBlock(block.blockKey(), block.blockKind(), block.displayOrder(),
                    null, null, block.markup());
        };
    }
}
