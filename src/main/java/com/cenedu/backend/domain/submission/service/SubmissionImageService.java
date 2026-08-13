package com.cenedu.backend.domain.submission.service;

import java.time.Duration;

import com.cenedu.backend.domain.problem.service.ProblemAnswerUnitService;
import com.cenedu.backend.domain.worksheet.service.WorksheetImageAccessService;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.infra.storage.config.S3Properties;
import com.cenedu.backend.infra.storage.service.ImageFileValidator;
import com.cenedu.backend.infra.storage.service.ImageStorageService;
import com.cenedu.backend.infra.storage.service.ValidatedImage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 학생 답안 이미지의 권한 검증과 S3 저장·조회를 조율한다. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
public class SubmissionImageService {

    private static final Duration ANSWER_IMAGE_URL_EXPIRATION = Duration.ofMinutes(10);

    private final WorksheetImageAccessService worksheetImageAccessService;
    private final ProblemAnswerUnitService answerUnitService;
    private final ImageFileValidator imageFileValidator;
    private final ImageStorageService imageStorageService;
    private final S3Properties s3Properties;

    /** 로그인 학생의 특정 답안 칸 이미지를 검증하고 저장한다. */
    public void upload(long memberId, UserRole role, long assignmentStudentId,
                       long answerUnitId, MultipartFile file) {
        if (role != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.IMAGE_ACCESS_DENIED);
        }
        validateTarget(memberId, role, assignmentStudentId, answerUnitId);
        ValidatedImage image = imageFileValidator.validate(file);
        imageStorageService.upload(
                s3Properties.requiredAnswerBucket(),
                answerKey(assignmentStudentId, answerUnitId),
                image.content(),
                image.contentType()
        );
    }

    /** 학생 본인 또는 담당 교사가 조회할 수 있는 답안 이미지 URL을 반환한다. */
    public String createGetUrl(long memberId, UserRole role, long assignmentStudentId,
                               long answerUnitId) {
        validateTarget(memberId, role, assignmentStudentId, answerUnitId);
        return imageStorageService.createGetUrl(
                s3Properties.requiredAnswerBucket(),
                answerKey(assignmentStudentId, answerUnitId),
                ANSWER_IMAGE_URL_EXPIRATION
        );
    }

    private void validateTarget(long memberId, UserRole role, long assignmentStudentId,
                                long answerUnitId) {
        long worksheetId = worksheetImageAccessService.getAuthorizedWorksheetId(
                memberId, role, assignmentStudentId);
        long questionId = answerUnitService.getQuestionId(answerUnitId);
        worksheetImageAccessService.validateQuestionIncluded(worksheetId, questionId);
    }

    private String answerKey(long assignmentStudentId, long answerUnitId) {
        return "answers/%d/%d".formatted(assignmentStudentId, answerUnitId);
    }
}
