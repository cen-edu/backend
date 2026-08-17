package com.cenedu.backend.domain.problem.authoring.port;

import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;

/**
 * 시스템 생성 조율측과 {@code ai.problem.adapter} 구현체 사이의 문항 1개 생성 경계다.
 *
 * <p>계약과 Adapter 구현 모두 이하영이 담당한다. Adapter는 구조화된 생성 명령만 받아
 * 공통 AI Client를 사용하고, Job·Session·Version을 직접 저장하지 않는다.
 */
public interface ProblemGenerationPort {

    /** 구조화된 조건으로 문제 후보 하나를 생성한다. */
    ProblemCandidateDraft generate(ProblemGenerationCommand command);
}
