package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.repository.ProblemAnswerUnitRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인이 답안 칸의 문항 정보를 조회할 수 있는 공개 서비스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemAnswerUnitService {

    private final ProblemAnswerUnitRepository answerUnitRepository;

    /** 답안 칸이 속한 문항 ID를 반환하고 존재하지 않으면 실패한다. */
    public long getQuestionId(long answerUnitId) {
        return answerUnitRepository.findQuestionIdById(answerUnitId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.IMAGE_ANSWER_UNIT_NOT_FOUND));
    }
}
