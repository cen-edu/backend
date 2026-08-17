package com.cenedu.backend.domain.problem.authoring.verification;

/** 생성과 수정의 검증 근거가 다르므로 호출 경로별 부가 정보를 표현하는 마커 계약이다. */
public sealed interface VerificationContext
        permits GenerationVerificationContext, EditVerificationContext {
}
