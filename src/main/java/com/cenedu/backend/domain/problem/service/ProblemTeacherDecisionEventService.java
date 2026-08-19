package com.cenedu.backend.domain.problem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.entity.ProblemTeacherDecisionEvent;
import com.cenedu.backend.domain.problem.entity.enums.TeacherDecisionType;
import com.cenedu.backend.domain.problem.repository.ProblemTeacherDecisionEventRepository;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ProblemTeacherDecisionEventService {
    private final ProblemTeacherDecisionEventRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public ProblemTeacherDecisionEventService(ProblemTeacherDecisionEventRepository repository) { this.repository = repository; }

    /** 교사가 현재 Version을 최종 승인한 결정을 기록한다. */
    public void recordApproval(long teacherId, long sessionId, long versionId) { record(teacherId, sessionId, versionId, TeacherDecisionType.APPROVED, null, null); }
    /** 교사가 구조화된 수정 실행을 시작한 결정을 기록한다. */
    public void recordModificationStarted(long teacherId, long sessionId, long baseVersionId, UUID requestId, List<ProblemEditInstruction> instructions) { record(teacherId, sessionId, baseVersionId, TeacherDecisionType.MODIFICATION_STARTED, instructions, requestId); }
    /** 교사가 과거 PASSED Version으로 복원한 결정을 기록한다. */
    public void recordRestore(long teacherId, long sessionId, long restoredVersionId, UUID requestId) { record(teacherId, sessionId, restoredVersionId, TeacherDecisionType.RESTORED, null, requestId); }
    /** 교사가 문제 전체 교체 결과를 채택한 결정을 기록한다. */
    public void recordReplacement(long teacherId, long sessionId, long replacementVersionId, UUID requestId, List<ProblemEditInstruction> instructions) { record(teacherId, sessionId, replacementVersionId, TeacherDecisionType.REPLACED, instructions, requestId); }
    /** 교사가 미확정 작성 세션을 폐기한 결정을 기록한다. */
    public void recordDiscard(long teacherId, long sessionId, Long currentVersionId) { record(teacherId, sessionId, currentVersionId, TeacherDecisionType.DISCARDED, null, null); }

    private void record(long teacherId, long sessionId, Long versionId, TeacherDecisionType type, List<ProblemEditInstruction> instructions, UUID requestId) {
        String key = UUID.nameUUIDFromBytes(("teacher-decision:" + type + ":" + teacherId + ":" + sessionId + ":" + (versionId == null ? 0 : versionId) + ":" + (requestId == null ? "none" : requestId)).getBytes(StandardCharsets.UTF_8)).toString();
        if (repository.existsByEventKey(key)) return;
        String natures = json(instructions == null ? List.of() : instructions.stream().map(ProblemEditInstruction::changeNature).filter(Objects::nonNull).map(Enum::name).distinct().sorted().toList());
        String targets = json(instructions == null ? List.of() : instructions.stream().map(ProblemEditInstruction::targetType).filter(Objects::nonNull).map(Enum::name).distinct().sorted().toList());
        repository.save(ProblemTeacherDecisionEvent.create(key, teacherId, sessionId, versionId, type, natures, targets));
    }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
}
