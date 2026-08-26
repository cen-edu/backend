package com.cenedu.backend.domain.worksheet.service;

import java.util.List;
import java.util.Set;

import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.domain.problem.dto.response.FinalizedProblemReferenceResponse;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.service.ProblemAuthoringFinalizationService;
import com.cenedu.backend.domain.problem.service.ProblemAuthoringJsonCodec;
import com.cenedu.backend.domain.problem.service.ProblemAuthoringVersionService;
import com.cenedu.backend.domain.problem.service.ProblemBankSnapshotQueryService;
import com.cenedu.backend.domain.problem.service.ProblemQuestionDetailService;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetItemEditApplyResponse;
import com.cenedu.backend.domain.worksheet.dto.response.WorksheetItemEditSessionResponse;
import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.QuestionType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미 저장된 학습지의 문항 하나를 다시 수정할 수 있게 여는 경계다.
 *
 * <p>생성 단계의 작성 Session은 학습지 저장과 함께 FINALIZED로 닫히고, FINALIZED Session은
 * {@code startCollecting}의 {@code requireDraft}에 막혀 더 이상 수정 대화를 받지 못한다. 그래서
 * 학습지에 담긴 문항은 유형과 무관하게 수정할 방법이 없었다. 여기서는 문항의 현재 내용을 담은
 * 새 DRAFT Session을 열어 기존 문제 수정 API를 그대로 쓰게 하고, 교사가 확정하면 그 결과를
 * 학습지 문항이 가리키는 문항 ID에 반영한다.
 *
 * <p>배포된 학습지는 학생이 이미 그 문항을 보고 있을 수 있어 교체를 막는다.
 */
@Service
@RequiredArgsConstructor
public class WorksheetItemEditService {

    /** 종합평가가 허용하는 문항 유형이다. 빈칸형은 자동 채점 배점 규칙이 달라 제외한다. */
    private static final Set<QuestionType> ASSESSMENT_QUESTION_TYPES =
            Set.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_INPUT, QuestionType.ESSAY);

    private final WorksheetRepository worksheetRepository;
    private final WorksheetItemRepository worksheetItemRepository;
    private final WorksheetAssignmentRepository worksheetAssignmentRepository;
    private final ProblemBankSnapshotQueryService bankSnapshotQueryService;
    private final ProblemAuthoringSessionRepository authoringSessionRepository;
    private final ProblemAuthoringVersionService authoringVersionService;
    private final ProblemAuthoringFinalizationService finalizationService;
    private final ProblemAuthoringJsonCodec jsonCodec;
    private final ProblemQuestionDetailService problemQuestionDetailService;

    /** 학습지 문항의 현재 내용을 최초 Version으로 갖는 새 수정 Session을 연다. */
    @Transactional
    public WorksheetItemEditSessionResponse openEditSession(long teacherId, long worksheetId,
                                                            long worksheetItemId) {
        WorksheetItem item = editableItem(teacherId, worksheetId, worksheetItemId);
        BankSnapshotResult bank = bankSnapshotQueryService
                .getSnapshots(List.of(item.getQuestionId())).getFirst();
        if (!bank.reusable() || bank.snapshot() == null) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
        ProblemAuthoringSession session = authoringSessionRepository
                .saveAndFlush(ProblemAuthoringSession.createIdle(teacherId));
        ProblemAuthoringVersion version = authoringVersionService.saveBankReuse(
                teacherId, session.getId(), item.getQuestionId(),
                jsonCodec.write(bank.snapshot()),
                jsonCodec.write(DraftAssetManifest.forBankReuse(bank.assetStorageKeys())));
        item.startEditSession(session.getId());
        return new WorksheetItemEditSessionResponse(item.getId(), session.getId(),
                version.getId(), item.getQuestionId());
    }

    /** 수정이 끝난 Session을 최종화하고 그 결과 문항으로 학습지 문항을 교체한다. */
    @Transactional
    public WorksheetItemEditApplyResponse applyEditSession(long teacherId, long worksheetId,
                                                           long worksheetItemId, long sessionId) {
        WorksheetItem item = editableItem(teacherId, worksheetId, worksheetItemId);
        Long previousQuestionId = item.getQuestionId();
        requireOpenedFrom(teacherId, sessionId, item);

        FinalizedProblemReferenceResponse finalized = finalizationService
                .finalizeForWorksheet(teacherId, List.of(sessionId)).getFirst();
        Long questionId = finalized.questionId();
        if (questionId == null) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_DATA_INVALID);
        }
        // 최종화된 Session은 FINALIZED가 되어 더 이상 수정 대화를 받지 못하므로 연결을 끊는다.
        // 남겨 두면 다음 수정 시도가 이미 닫힌 Session을 유효한 것으로 착각한다. 아래 검증에서
        // 예외가 나가면 이 transaction 전체가 되돌아가므로 최종화와 함께 연결도 원상 복구된다.
        item.clearEditSession();
        if (questionId.equals(previousQuestionId)) {
            return new WorksheetItemEditApplyResponse(item.getId(), previousQuestionId,
                    questionId, false);
        }
        validateReplacement(item.getWorksheet(), worksheetId, questionId);
        item.replaceQuestion(questionId);
        return new WorksheetItemEditApplyResponse(item.getId(), previousQuestionId, questionId, true);
    }

    /** 교사가 소유한, 아직 배포하지 않은 학습지의 문항만 수정 대상으로 통과시킨다. */
    private WorksheetItem editableItem(long teacherId, long worksheetId, long worksheetItemId) {
        Worksheet worksheet = worksheetRepository
                .findByIdAndOwnerTeacherIdAndDeletedAtIsNull(worksheetId, teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_NOT_FOUND));
        WorksheetItem item = worksheetItemRepository.findById(worksheetItemId)
                .filter(candidate -> candidate.getWorksheet().getId().equals(worksheet.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSHEET_ITEM_NOT_FOUND));
        if (worksheetAssignmentRepository.existsByWorksheetId(worksheetId)) {
            throw new BusinessException(ErrorCode.WORKSHEET_ITEM_NOT_EDITABLE);
        }
        return item;
    }

    /**
     * 이 Session이 정말 이 문항에서 열린 것인지 문항에 기록된 연결로 확인한다.
     *
     * <p>확인하지 않으면 교사가 소유한 아무 Session이나 이 문항에 갖다 붙일 수 있고, 같은 요청을
     * 두 번 보내면 이미 교체된 문항을 한 번 더 최종화하려다 엉뚱한 상태가 된다. 반영이 끝나면
     * 연결이 끊기므로 두 번째 요청은 여기서 걸린다.
     */
    private void requireOpenedFrom(long teacherId, long sessionId, WorksheetItem item) {
        authoringSessionRepository.findByIdAndOwnerTeacherId(sessionId, teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        if (!Long.valueOf(sessionId).equals(item.getEditingSessionId())) {
            throw new BusinessException(ErrorCode.WORKSHEET_ITEM_EDIT_SESSION_MISMATCH);
        }
    }

    /** 교체 결과가 학습지 종류의 문항 유형 규칙과 중복 금지를 지키는지 확인한다. */
    private void validateReplacement(Worksheet worksheet, long worksheetId, Long questionId) {
        if (worksheetItemRepository.existsByWorksheetIdAndQuestionId(worksheetId, questionId)) {
            throw new BusinessException(ErrorCode.WORKSHEET_QUESTION_DUPLICATED);
        }
        QuestionType questionType = problemQuestionDetailService
                .getQuestionTypes(List.of(questionId)).get(questionId);
        if (questionType == null) {
            throw new BusinessException(ErrorCode.WORKSHEET_QUESTION_NOT_FOUND);
        }
        if (worksheet.getType() == WorksheetType.GENERAL_LEARNING
                && questionType != QuestionType.STEP_FILL) {
            throw new BusinessException(ErrorCode.WORKSHEET_TYPE_MISMATCH);
        }
        if (worksheet.getType() == WorksheetType.COMPREHENSIVE_ASSESSMENT
                && !ASSESSMENT_QUESTION_TYPES.contains(questionType)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_QUESTION_TYPE_NOT_ALLOWED);
        }
    }
}
