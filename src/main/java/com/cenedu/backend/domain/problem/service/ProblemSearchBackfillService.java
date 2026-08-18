package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.domain.problem.authoring.snapshot.SearchSnapshotNormalizer;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProblemSearchBackfillService {
    private final ProblemQuestionRepository questionRepository;
    private final ProblemBankSnapshotQueryService snapshotService;
    private final ProblemSearchIndexingService indexingService;
    private final SearchSnapshotNormalizer normalizer;
    private final SnapshotStructuralValidator validator;

    public ProblemSearchBackfillService(ProblemQuestionRepository questionRepository,
            ProblemBankSnapshotQueryService snapshotService, ProblemSearchIndexingService indexingService) {
        this(questionRepository, snapshotService, indexingService, new SearchSnapshotNormalizer(), null);
    }

    public ProblemSearchBackfillService(ProblemQuestionRepository questionRepository,
            ProblemBankSnapshotQueryService snapshotService, ProblemSearchIndexingService indexingService,
            SearchSnapshotNormalizer normalizer) {
        this(questionRepository, snapshotService, indexingService, normalizer, null);
    }

    @Autowired
    public ProblemSearchBackfillService(ProblemQuestionRepository questionRepository,
            ProblemBankSnapshotQueryService snapshotService, ProblemSearchIndexingService indexingService,
            SearchSnapshotNormalizer normalizer, SnapshotStructuralValidator validator) {
        this.questionRepository = questionRepository; this.snapshotService = snapshotService;
        this.indexingService = indexingService; this.normalizer = normalizer; this.validator = validator;
    }

    /** 커서 뒤의 검증 가능한 문항을 batch 크기만큼 검사해 큐에 넣고 다음 커서를 반환한다. */
    public BackfillBatchResult enqueueBatch(long afterQuestionId, int batchSize) {
        if (batchSize < 1) throw new IllegalArgumentException("backfill batch size는 1 이상이어야 합니다.");
        List<ProblemQuestion> questions = questionRepository.findByIdGreaterThanAndDeletedAtIsNullOrderByIdAsc(afterQuestionId, PageRequest.of(0, batchSize));
        if (questions.isEmpty()) return new BackfillBatchResult(afterQuestionId, 0, 0, 0, true);
        List<Long> ids = questions.stream().map(ProblemQuestion::getId).toList();
        var results = snapshotService.getSnapshots(ids).stream().collect(java.util.stream.Collectors.toMap(BankSnapshotResult::questionId, r -> r));
        int enqueued = 0, rejected = 0;
        for (ProblemQuestion question : questions) {
            BankSnapshotResult result = results.get(question.getId());
            if (result != null && result.snapshot() != null
                    && question.getQuestionType() != QuestionType.ESSAY) {
                var normalized = normalizer.normalize(result.snapshot());
                boolean valid = validator == null || validator.violations(normalized).isEmpty();
                if (valid && indexingService.enqueueImported(question.getId(), normalized)) enqueued++;
                else rejected++;
            } else {
                rejected++;
            }
        }
        long next = questions.getLast().getId();
        return new BackfillBatchResult(next, questions.size(), enqueued, rejected, questions.size() < batchSize);
    }

    public record BackfillBatchResult(long nextQuestionId, int scanned, int enqueued, int rejected, boolean exhausted) {}
}
