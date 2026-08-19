package com.cenedu.backend.domain.submission.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 학생 답안 이미지의 권한 검증과 S3 저장·조회를 조율한다. */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
public class SubmissionImageService {

    /** 교사 채점 화면·학생 결과 화면이 여는 URL. 사람이 보는 동안만 살아 있으면 된다. */
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

    /**
     * 한 배정의 여러 칸에 대한 조회 URL을 <b>한 번의 배정 권한 검증</b>으로 만든다.
     *
     * <p>{@link #createGetUrl}을 칸마다 부르면 칸마다 {@code validateTarget}이 돌아 배정·학습지
     * 조회가 반복된다. 채점 화면은 한 학생이 수십 칸이라 그 자리에서 N+1이 난다.
     *
     * <p>배정 권한은 1회만 검증하고, 칸이 이 학습지 문항인지는 칸마다 확인한다 — 검증을 줄이는 게
     * 아니라 같은 검증을 반복하지 않게 하는 것이다.
     *
     * <p>버킷에 객체가 없는 칸은 결과에서 빠진다(호출부의 {@code get}이 {@code null}). 업로드 실패나
     * 수명주기 삭제로 한 장이 비었다고 던지면 교사가 그 학생 답안을 통째로 못 연다. 저장소 장애·설정
     * 누락은 그대로 올린다 — 그것까지 감추면 S3가 죽은 상황이 "필기 없는 답안"으로 보인다.
     */
    public Map<Long, String> createGetUrls(long memberId, UserRole role, long assignmentStudentId,
                                           List<Long> answerUnitIds) {
        if (answerUnitIds.isEmpty()) {
            return Map.of();
        }
        long worksheetId = worksheetImageAccessService.getAuthorizedWorksheetId(
                memberId, role, assignmentStudentId);

        Map<Long, String> urlsByAnswerUnitId = new LinkedHashMap<>();
        for (Long answerUnitId : answerUnitIds) {
            long questionId = answerUnitService.getQuestionId(answerUnitId);
            worksheetImageAccessService.validateQuestionIncluded(worksheetId, questionId);
            try {
                urlsByAnswerUnitId.put(answerUnitId, imageStorageService.createGetUrl(
                        s3Properties.requiredAnswerBucket(),
                        answerKey(assignmentStudentId, answerUnitId),
                        ANSWER_IMAGE_URL_EXPIRATION
                ));
            } catch (BusinessException exception) {
                if (exception.getErrorCode() != ErrorCode.IMAGE_NOT_FOUND) {
                    throw exception;
                }
                log.warn("필기 이미지가 버킷에 없어 URL 없이 내려보낸다 — assignmentStudentId={}, answerUnitId={}",
                        assignmentStudentId, answerUnitId);
            }
        }
        return urlsByAnswerUnitId;
    }

    /**
     * 서술형 채점 파이프라인이 칸 하나를 LLM 에 넘기기 직전에 발급하는 URL(D4).
     *
     * <p><b>권한을 검사하지 않는다.</b> 검사할 주체가 없어서다 — 이 경로는 사람이 부르는 요청이
     * 아니라 배치가 도는 시스템 트리거고, 교사 소유권은 채점을 시작한
     * {@code GradingExecutionService.start} 가 이미 확인했다. 사람이 부르는 두 메서드는
     * {@code validateTarget} 을 그대로 통과하므로 이 메서드가 그 검사를 무르지 않는다.
     *
     * <p><b>미리 모아 발급하지 않는다.</b> 칸마다 채점 직전에 만든다. 그래서 이 URL 은 한 칸이
     * 채점되는 동안만 살면 되고, 배치 전체 소요와는 무관하다 — 만료는 화면용과 별개로
     * {@code app.storage.s3.grading-pipeline-url-expiration} 이 정한다.
     */
    public String createGradingPipelineUrl(long assignmentStudentId, long answerUnitId) {
        return imageStorageService.createGetUrl(
                s3Properties.requiredAnswerBucket(),
                answerKey(assignmentStudentId, answerUnitId),
                s3Properties.gradingPipelineUrlExpiration()
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
