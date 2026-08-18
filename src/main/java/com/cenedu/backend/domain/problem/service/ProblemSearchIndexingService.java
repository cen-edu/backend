package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.search.SearchIndexingCommand;
import com.cenedu.backend.domain.problem.authoring.search.SearchIndexingPort;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProblemSearchIndexingService {
    private final ProblemQuestionRepository questionRepository;
    private final CurriculumUnitQueryService curriculumService;
    private final SearchIndexingPort indexingPort;
    private final ProblemRagProperties properties;

    public ProblemSearchIndexingService(ProblemQuestionRepository questionRepository,
            CurriculumUnitQueryService curriculumService, SearchIndexingPort indexingPort,
            ProblemRagProperties properties) {
        this.questionRepository = questionRepository; this.curriculumService = curriculumService;
        this.indexingPort = indexingPort; this.properties = properties;
    }

    /** 최종 승인 문항을 검색 인덱싱 큐에 멱등 등록한다. */
    public boolean enqueueFinalized(long questionId, long authoringVersionId, QuestionSnapshotV1 snapshot) {
        return enqueue(questionId, authoringVersionId, snapshot);
    }

    /** 검증된 적재 문항 Snapshot을 검색 인덱싱 큐에 멱등 등록한다. */
    public boolean enqueueImported(long questionId, QuestionSnapshotV1 snapshot) {
        return enqueue(questionId, null, snapshot);
    }

    private boolean enqueue(long questionId, Long versionId, QuestionSnapshotV1 snapshot) {
        if (!properties.enabled() || !properties.indexing().enabled()) return false;
        ProblemQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("검색 인덱싱 문항이 없습니다."));
        CurriculumPathResponse path = curriculumService.getPathsBySubUnitIds(Set.of(question.getSubUnitId())).get(question.getSubUnitId());
        CurriculumScope scope = new CurriculumScope(path.curriculumRevision(), path.schoolLevel(), path.grade(),
                path.semester() == null ? null : path.semester().intValue(), path.achievementStandardId(),
                path.subUnitId(), path.majorUnitName(), path.middleUnitName(), path.subUnitName());
        UUID key = UUID.nameUUIDFromBytes(("problem-search:" + questionId).getBytes(StandardCharsets.UTF_8));
        return indexingPort.enqueue(new SearchIndexingCommand(key, questionId, versionId, scope,
                question.getSourceRef(), snapshot, Set.of()));
    }
}
