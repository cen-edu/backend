package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.domain.problem.authoring.snapshot.SearchSnapshotNormalizer;
import com.cenedu.backend.domain.problem.authoring.search.SearchIndexMaintenancePort;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.authoring.visual.VisualSnapshotConsistencyValidator;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProblemSearchBackfillService {
    private final ProblemQuestionRepository questionRepository;
    private final ProblemBankSnapshotQueryService snapshotService;
    private final ProblemSearchIndexingService indexingService;
    private final SearchSnapshotNormalizer normalizer;
    private final SnapshotStructuralValidator validator;
    private final VisualSnapshotConsistencyValidator visualValidator;
    private final SearchIndexMaintenancePort maintenancePort;

    @Autowired
    public ProblemSearchBackfillService(ProblemQuestionRepository questionRepository,
            ProblemBankSnapshotQueryService snapshotService, ProblemSearchIndexingService indexingService,
            SearchSnapshotNormalizer normalizer, SnapshotStructuralValidator validator,
            VisualSnapshotConsistencyValidator visualValidator, SearchIndexMaintenancePort maintenancePort) {
        this.questionRepository = questionRepository; this.snapshotService = snapshotService;
        this.indexingService = indexingService; this.normalizer = normalizer; this.validator = validator;
        this.visualValidator = visualValidator; this.maintenancePort = maintenancePort;
    }

    /** 커서 뒤의 검증 가능한 문항을 batch 크기만큼 검사해 큐에 넣고 다음 커서를 반환한다. */
    public BackfillBatchResult enqueueBatch(long afterQuestionId, int batchSize) {
        if (batchSize < 1) throw new IllegalArgumentException("backfill batch size는 1 이상이어야 합니다.");
        List<Long> ids = maintenancePort.findActiveMissingQuestionIds(afterQuestionId, batchSize);
        if (ids.isEmpty()) return new BackfillBatchResult(afterQuestionId, 0, 0, 0, 0, true);
        var questionById = questionRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(ProblemQuestion::getId, question -> question));
        var results = snapshotService.getSnapshots(ids).stream().collect(java.util.stream.Collectors.toMap(BankSnapshotResult::questionId, r -> r));
        int enqueued = 0, unchanged = 0, rejected = 0;
        for (Long questionId : ids) {
            ProblemQuestion question = questionById.get(questionId);
            if (question == null) {
                rejected++;
                continue;
            }
            BankSnapshotResult result = results.get(question.getId());
            if (result != null && result.snapshot() != null
                    && question.getQuestionType() != QuestionType.ESSAY) {
                var normalized = normalizer.normalize(result.snapshot());
                boolean valid = result.reusable()
                        && (validator == null || validator.violations(normalized).isEmpty())
                        && visualValidator.violations(normalized).isEmpty();
                if (!valid) {
                    rejected++;
                } else if (indexingService.enqueueImported(question.getId(), normalized,
                        result.assetStorageKeys())) {
                    enqueued++;
                } else {
                    unchanged++;
                }
            } else {
                rejected++;
            }
        }
        long next = ids.getLast();
        return new BackfillBatchResult(next, ids.size(), enqueued, unchanged, rejected,
                ids.size() < batchSize);
    }

    public record BackfillBatchResult(long nextQuestionId, int scanned, int enqueued,
            int unchanged, int rejected, boolean exhausted) {}
}
