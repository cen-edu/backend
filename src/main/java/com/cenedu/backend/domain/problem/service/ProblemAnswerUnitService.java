package com.cenedu.backend.domain.problem.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
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

    /**
     * 채점에 필요한 정답값만 배치로 반환한다. 문항 상세(발문·보기·단계·해설)를 끌고 오지 않는다.
     *
     * <p>존재하지 않는 ID는 결과에서 빠진다 — 실패시키지 않는 것은 한 칸이 없다고 배치 전체를
     * 멈추면 안 되기 때문이다. 호출부가 누락된 칸을 채점 실패로 기록한다.
     */
    public Map<Long, AnswerKey> getAnswerKeysByIds(List<Long> answerUnitIds) {
        if (answerUnitIds.isEmpty()) {
            return Map.of();
        }
        return answerUnitRepository.findAllById(answerUnitIds).stream()
                .collect(Collectors.toMap(ProblemAnswerUnit::getId, AnswerKey::from));
    }

    /**
     * 문항별 답안 칸 수를 배치로 센다. 진행률 분모(일반·맞춤 학습의 {@code totalUnits})가
     * 이 값을 쓴다.
     *
     * <p>칸이 하나도 없는 문항은 결과에서 빠진다. 호출부가 0으로 본다.
     */
    public Map<Long, Long> countByQuestionIds(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return answerUnitRepository.findAllByQuestionIds(questionIds).stream()
                .collect(Collectors.groupingBy(
                        unit -> unit.getQuestion().getId(), Collectors.counting()));
    }

    /**
     * 채점기가 쓰는 정답값.
     *
     * <p>{@code compareMethod}는 담지 않는다 — 채점 기준은 {@code submission_answer}에 저장된
     * 스냅샷이고, 여기서도 내려주면 어느 쪽이 기준인지 흐려진다.
     */
    public record AnswerKey(Long answerUnitId, String answerRaw, String answerNormalized,
                            String displayUnit) {

        private static AnswerKey from(ProblemAnswerUnit answerUnit) {
            return new AnswerKey(answerUnit.getId(), answerUnit.getAnswerRaw(),
                    answerUnit.getAnswerNormalized(), answerUnit.getDisplayUnit());
        }
    }
}
