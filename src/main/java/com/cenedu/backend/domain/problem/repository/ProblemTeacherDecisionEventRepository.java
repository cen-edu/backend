package com.cenedu.backend.domain.problem.repository;

import com.cenedu.backend.domain.problem.entity.ProblemTeacherDecisionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemTeacherDecisionEventRepository extends JpaRepository<ProblemTeacherDecisionEvent, Long> {
    /** 동일 업무 이벤트 키가 이미 저장됐는지 확인한다. */
    boolean existsByEventKey(String eventKey);
}
