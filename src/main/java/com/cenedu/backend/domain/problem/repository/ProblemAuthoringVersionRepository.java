package com.cenedu.backend.domain.problem.repository;

import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemAuthoringVersionRepository
        extends JpaRepository<ProblemAuthoringVersion, Long> {

    // 교사에게 보여줄 Session의 전체 버전 이력을 V1부터 조회한다.
    List<ProblemAuthoringVersion> findAllBySessionIdOrderByVersionNo(Long sessionId);

    // 복원 요청이 지칭한 Session 내 Version 번호를 조회한다.
    Optional<ProblemAuthoringVersion> findBySessionIdAndVersionNo(Long sessionId, int versionNo);

    // 다른 Session의 Version을 승격·복원하지 못하게 ID와 Session ID를 함께 검사한다.
    Optional<ProblemAuthoringVersion> findByIdAndSessionId(Long id, Long sessionId);

    // Session lock 안에서 다음 versionNo를 배정할 때 가장 최근 Version을 조회한다.
    Optional<ProblemAuthoringVersion> findFirstBySessionIdOrderByVersionNoDesc(Long sessionId);
}
