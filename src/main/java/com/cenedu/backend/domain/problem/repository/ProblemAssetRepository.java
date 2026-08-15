package com.cenedu.backend.domain.problem.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.problem.entity.ProblemAsset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemAssetRepository
    extends JpaRepository<ProblemAsset, Long> {

    // 여러 문항의 이미지 자산을 문항별 표시 순서로 일괄 조회한다.
    @Query("""
        select asset
        from ProblemAsset asset
        where asset.question.id in :questionIds
        order by asset.question.id,
                 asset.displayOrder,
                 asset.id
        """)
    List<ProblemAsset> findAllByQuestionIds(
        @Param("questionIds") Collection<Long> questionIds
    );
    /** 문항의 지정된 이미지 키에 대응하는 자산을 반환한다. */
    Optional<ProblemAsset> findByQuestionIdAndAssetKey(Long questionId, String assetKey);
}
