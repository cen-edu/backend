package com.cenedu.backend.domain.problem.repository;

import java.util.Optional;

import com.cenedu.backend.domain.problem.entity.ProblemAsset;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemAssetRepository extends JpaRepository<ProblemAsset, Long> {

    /** 문항의 지정된 이미지 키에 대응하는 자산을 반환한다. */
    Optional<ProblemAsset> findByQuestionIdAndAssetKey(Long questionId, String assetKey);
}
