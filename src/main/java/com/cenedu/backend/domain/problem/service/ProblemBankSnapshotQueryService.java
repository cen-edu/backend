package com.cenedu.backend.domain.problem.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.snapshot.ProblemQuestionSnapshotMapper;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.dto.response.ProblemQuestionDetailResponse;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문제은행 후보를 상세 데이터와 S1 구조 검증 결과로 변환하는 조회 경계다. */
@Service
public class ProblemBankSnapshotQueryService {
    private final ProblemQuestionRepository questionRepository;
    private final ProblemQuestionDetailService detailService;
    private final ProblemQuestionSnapshotMapper mapper;
    private final SnapshotStructuralValidator validator;

    public ProblemBankSnapshotQueryService(ProblemQuestionRepository questionRepository,
                                           ProblemQuestionDetailService detailService,
                                           ProblemQuestionSnapshotMapper mapper,
                                           SnapshotStructuralValidator validator) {
        this.questionRepository = questionRepository;
        this.detailService = detailService;
        this.mapper = mapper;
        this.validator = validator;
    }

    /** 문제 ID 순서를 유지하며 스냅샷을 만들고 재사용 가능 여부를 판정한다. */
    @Transactional(readOnly = true)
    public List<BankSnapshotResult> getSnapshots(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) return List.of();
        Map<Long, ProblemQuestion> questions = questionRepository.findAllById(questionIds).stream()
                .collect(java.util.stream.Collectors.toMap(ProblemQuestion::getId, q -> q,
                        (first, second) -> first, LinkedHashMap::new));
        Map<Long, ProblemQuestionDetailResponse> details = detailService.getDetailsByIds(questionIds)
                .stream().collect(java.util.stream.Collectors.toMap(ProblemQuestionDetailResponse::id,
                        detail -> detail, (first, second) -> first, LinkedHashMap::new));
        return questionIds.stream().map(id -> result(id, questions.get(id), details.get(id))).toList();
    }

    private BankSnapshotResult result(Long id, ProblemQuestion question,
                                      ProblemQuestionDetailResponse detail) {
        if (question == null) return new BankSnapshotResult(id, null, false, List.of("question: 존재하지 않습니다."));
        QuestionSnapshotV1 snapshot = mapper.toSnapshot(question, detail);
        List<String> violations = validator.violations(snapshot);
        return new BankSnapshotResult(id, snapshot, violations.isEmpty(), violations);
    }
}
