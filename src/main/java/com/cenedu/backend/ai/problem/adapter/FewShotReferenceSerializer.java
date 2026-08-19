package com.cenedu.backend.ai.problem.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;
import com.cenedu.backend.domain.problem.authoring.model.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FewShotReferenceSerializer {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 참고 Snapshot을 정답 없는 Few-shot JSON 배열로 직렬화한다. */
    public String serialize(CurriculumScope curriculum, List<GenerationReference> references) {
        List<Map<String, Object>> items = (references == null ? List.<GenerationReference>of() : references).stream()
                .map(reference -> projection(curriculum, reference)).toList();
        try { return objectMapper.writeValueAsString(items); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Few-shot JSON 생성에 실패했습니다.", exception); }
    }

    private Map<String, Object> projection(CurriculumScope curriculum, GenerationReference reference) {
        QuestionSnapshotV1 snapshot = reference.snapshot();
        SnapshotMetadata metadata = snapshot.metadata();
        Map<String, Object> curriculumJson = new java.util.LinkedHashMap<>();
        curriculumJson.put("revision", curriculum.curriculumRevision()); curriculumJson.put("schoolLevel", curriculum.schoolLevel());
        curriculumJson.put("grade", curriculum.grade()); curriculumJson.put("semester", curriculum.semester());
        curriculumJson.put("achievementStandardId", curriculum.achievementStandardId()); curriculumJson.put("subUnitId", curriculum.subUnitId());
        curriculumJson.put("majorUnitName", curriculum.majorUnitName()); curriculumJson.put("middleUnitName", curriculum.middleUnitName());
        curriculumJson.put("subUnitName", curriculum.subUnitName());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("role", reference.role().name()); result.put("sourceQuestionId", reference.sourceQuestionId());
        result.put("curriculum", curriculumJson); result.put("questionType", metadata.questionType().name()); result.put("difficulty", metadata.difficulty());
        result.put("prompt", snapshot.contentBlocks().stream().sorted(Comparator.comparingInt(SnapshotContentBlock::displayOrder)).map(SnapshotContentBlock::text).filter(java.util.Objects::nonNull).toList());
        result.put("choices", snapshot.choices().stream().sorted(Comparator.comparingInt(SnapshotChoice::displayOrder)).map(SnapshotChoice::content).toList());
        result.put("steps", snapshot.steps().stream().sorted(Comparator.comparingInt(SnapshotStep::displayOrder)).map(this::step).toList());
        result.put("solutionStrategy", strategy(snapshot.learningGuide())); result.put("visualSummary", visual(metadata.presentation())); result.put("directCopyForbidden", true);
        return result;
    }

    private Map<String, Object> step(SnapshotStep step) {
        List<String> segments = step.segments().stream().map(segment -> segment.type() == SnapshotSegmentType.BLANK
                ? "<BLANK>" : segment.text()).toList();
        return Map.of("label", step.label(), "segments", segments);
    }
    private String strategy(SnapshotLearningGuide guide) {
        if (guide == null) return "풀이 전략 없음";
        if (guide.keyPoints() != null && !guide.keyPoints().isEmpty()) return String.join(" | ", guide.keyPoints());
        return guide.conceptTitle() == null ? "풀이 전략 없음" : guide.conceptTitle();
    }
    private String visual(com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation presentation) {
        return switch (presentation) { case TEXT_ONLY -> "text-only"; case WITH_FIGURE -> "figure"; case WITH_TABLE -> "table"; };
    }
}
