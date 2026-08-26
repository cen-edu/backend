package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.repository.ProblemAssetRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.QuestionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProblemQuestionSelector {

    private final ProblemQuestionRepository problemQuestionRepository;
    private final ProblemAssetRepository problemAssetRepository;

    /** 재고가 부족해도 가능한 수만 반환하여 AI 부족분 계산에 사용한다. */
    public List<ProblemQuestion> selectAvailable(Long subUnitId, short difficulty,
                                                  QuestionType questionType, int count,
                                                  Set<Long> excludedQuestionIds) {
        return selectAvailable(subUnitId, difficulty, questionType, count, excludedQuestionIds, null);
    }

    /** 자료 보유 조건을 전체 후보에 먼저 적용한 뒤 무작위 선택 수를 제한한다. */
    public List<ProblemQuestion> selectAvailable(Long subUnitId, short difficulty,
                                                  QuestionType questionType, int count,
                                                  Set<Long> excludedQuestionIds,
                                                  Boolean requiresAsset) {
        if (count <= 0) return List.of();
        List<ProblemQuestion> candidates = new ArrayList<>(problemQuestionRepository
            .findAllBySubUnitIdAndDifficultyAndQuestionTypeAndDeletedAtIsNull(
                subUnitId, difficulty, questionType));
        candidates.removeIf(question -> excludedQuestionIds.contains(question.getId()));
        filterByAssetRequirement(candidates, requiresAsset);
        Collections.shuffle(candidates);
        return List.copyOf(candidates.subList(0, Math.min(count, candidates.size())));
    }

    private void filterByAssetRequirement(List<ProblemQuestion> candidates, Boolean requiresAsset) {
        if (requiresAsset == null || candidates.isEmpty()) return;
        Set<Long> assetQuestionIds = Set.copyOf(problemAssetRepository.findQuestionIdsWithAssets(
                candidates.stream().map(ProblemQuestion::getId).toList()));
        candidates.removeIf(question -> requiresAsset != assetQuestionIds.contains(question.getId()));
    }

    /**
     * 출제 조건에 맞는 문항을 중복 없이 무작위로 선택한다.
     */
    public List<ProblemQuestion> select(
        Long subUnitId,
        short difficulty,
        QuestionType questionType,
        int count,
        Set<Long> excludedQuestionIds
    ) {
        List<ProblemQuestion> candidates =
            new ArrayList<>(
                problemQuestionRepository
                    .findAllBySubUnitIdAndDifficultyAndQuestionTypeAndDeletedAtIsNull(
                        subUnitId,
                        difficulty,
                        questionType
                    )
            );

        candidates.removeIf(
            question -> excludedQuestionIds.contains(question.getId())
        );

        if (candidates.size() < count) {
            throw new BusinessException(
                ErrorCode.QUESTION_INVENTORY_INSUFFICIENT
            );
        }

        Collections.shuffle(candidates);

        return List.copyOf(candidates.subList(0, count));
    }
}
