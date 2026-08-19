package com.cenedu.backend.domain.problem.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.snapshot.ProblemQuestionSnapshotMapper;
import com.cenedu.backend.domain.problem.authoring.snapshot.ProblemSnapshotSource;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemChoiceRepository;
import com.cenedu.backend.domain.problem.repository.ProblemStepRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAnswerUnitRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAssetRepository;
import com.cenedu.backend.domain.problem.repository.ProblemRubricItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문제은행 후보를 상세 데이터와 S1 구조 검증 결과로 변환하는 조회 경계다. */
@Service
public class ProblemBankSnapshotQueryService {
    private final ProblemQuestionRepository questionRepository;
    private final ProblemQuestionSnapshotMapper mapper;
    private final SnapshotStructuralValidator validator;
    private final ProblemChoiceRepository choiceRepository;
    private final ProblemStepRepository stepRepository;
    private final ProblemAnswerUnitRepository answerUnitRepository;
    private final ProblemAssetRepository assetRepository;
    private final ProblemRubricItemRepository rubricRepository;

    public ProblemBankSnapshotQueryService(ProblemQuestionRepository questionRepository,
                                           ProblemQuestionSnapshotMapper mapper,
                                           SnapshotStructuralValidator validator,
                                           ProblemChoiceRepository choiceRepository,
                                           ProblemStepRepository stepRepository,
                                           ProblemAnswerUnitRepository answerUnitRepository,
                                           ProblemAssetRepository assetRepository,
                                           ProblemRubricItemRepository rubricRepository) {
        this.questionRepository = questionRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.choiceRepository = choiceRepository;
        this.stepRepository = stepRepository;
        this.answerUnitRepository = answerUnitRepository;
        this.assetRepository = assetRepository;
        this.rubricRepository = rubricRepository;
    }

    /** 문제 ID 순서를 유지하며 스냅샷을 만들고 재사용 가능 여부를 판정한다. */
    @Transactional(readOnly = true)
    public List<BankSnapshotResult> getSnapshots(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) return List.of();
        Map<Long, ProblemQuestion> questions = questionRepository.findAllById(questionIds).stream()
                .collect(java.util.stream.Collectors.toMap(ProblemQuestion::getId, q -> q,
                        (first, second) -> first, LinkedHashMap::new));
        Map<Long, List<com.cenedu.backend.domain.problem.entity.ProblemChoice>> choices = group(choiceRepository.findAllByQuestionIds(questionIds));
        Map<Long, List<com.cenedu.backend.domain.problem.entity.ProblemStep>> steps = group(stepRepository.findAllByQuestionIds(questionIds));
        Map<Long, List<com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit>> units = group(answerUnitRepository.findAllByQuestionIds(questionIds));
        Map<Long, List<com.cenedu.backend.domain.problem.entity.ProblemAsset>> assets = group(assetRepository.findAllByQuestionIds(questionIds));
        Map<Long, List<com.cenedu.backend.domain.problem.entity.ProblemRubricItem>> rubrics = group(rubricRepository.findAllByQuestionIds(questionIds));
        return questionIds.stream().map(id -> result(id, questions.get(id),
                choices.getOrDefault(id, List.of()), steps.getOrDefault(id, List.of()),
                units.getOrDefault(id, List.of()), assets.getOrDefault(id, List.of()),
                rubrics.getOrDefault(id, List.of()))).toList();
    }

    private BankSnapshotResult result(Long id, ProblemQuestion question,
                                      List<com.cenedu.backend.domain.problem.entity.ProblemChoice> choices,
                                      List<com.cenedu.backend.domain.problem.entity.ProblemStep> steps,
                                      List<com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit> units,
                                      List<com.cenedu.backend.domain.problem.entity.ProblemAsset> assets,
                                      List<com.cenedu.backend.domain.problem.entity.ProblemRubricItem> rubrics) {
        if (question == null) return new BankSnapshotResult(id, null, false, List.of("question: 존재하지 않습니다."));
        try {
            QuestionSnapshotV1 snapshot = mapper.toSnapshot(new ProblemSnapshotSource(
                    question, choices, steps, units, assets, rubrics));
            List<String> violations = validator.violations(snapshot);
            Map<String, String> storageKeys = assets.stream().collect(
                    java.util.stream.Collectors.toMap(
                            com.cenedu.backend.domain.problem.entity.ProblemAsset::getAssetKey,
                            com.cenedu.backend.domain.problem.entity.ProblemAsset::getStorageKey,
                            (first, second) -> first, LinkedHashMap::new));
            return new BankSnapshotResult(id, snapshot, violations.isEmpty(), violations, storageKeys);
        } catch (RuntimeException exception) {
            return new BankSnapshotResult(id, null, false,
                    List.of("snapshot mapping: " + exception.getMessage()));
        }
    }

    private <T> Map<Long, List<T>> group(List<T> values) {
        return values.stream().collect(java.util.stream.Collectors.groupingBy(value -> {
            if (value instanceof com.cenedu.backend.domain.problem.entity.ProblemChoice v) return v.getQuestion().getId();
            if (value instanceof com.cenedu.backend.domain.problem.entity.ProblemStep v) return v.getQuestion().getId();
            if (value instanceof com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit v) return v.getQuestion().getId();
            if (value instanceof com.cenedu.backend.domain.problem.entity.ProblemAsset v) return v.getQuestion().getId();
            return ((com.cenedu.backend.domain.problem.entity.ProblemRubricItem) value).getQuestion().getId();
        }, java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
    }
}
