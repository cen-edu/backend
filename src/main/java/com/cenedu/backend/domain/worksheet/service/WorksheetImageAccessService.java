package com.cenedu.backend.domain.worksheet.service;

import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.UserRole;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 답안 이미지 요청자가 학생별 학습지 수행에 접근할 수 있는지 확인한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorksheetImageAccessService {

    private final WorksheetAssignmentStudentRepository assignmentStudentRepository;
    private final WorksheetItemRepository worksheetItemRepository;

    /** JWT 사용자에게 수행 회차 접근 권한이 있는지 확인하고 학습지 ID를 반환한다. */
    public long getAuthorizedWorksheetId(long memberId, UserRole role,
                                         long assignmentStudentId) {
        WorksheetAssignmentStudent assignmentStudent = assignmentStudentRepository
                .findById(assignmentStudentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.IMAGE_ASSIGNMENT_NOT_FOUND));

        Worksheet worksheet = assignmentStudent.getAssignment().getWorksheet();
        boolean allowed = switch (role) {
            case STUDENT -> assignmentStudent.getStudentId().equals(memberId);
            case TEACHER -> worksheet.getOwnerTeacherId().equals(memberId);
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.IMAGE_ACCESS_DENIED);
        }
        return worksheet.getId();
    }

    /** 답안 칸이 속한 문항이 해당 학습지에 포함됐는지 확인한다. */
    public void validateQuestionIncluded(long worksheetId, long questionId) {
        if (!worksheetItemRepository.existsByWorksheetIdAndQuestionId(
                worksheetId, questionId)) {
            throw new BusinessException(ErrorCode.IMAGE_ANSWER_UNIT_NOT_ASSIGNED);
        }
    }
}
