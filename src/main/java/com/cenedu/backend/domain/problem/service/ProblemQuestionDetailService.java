package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService;
import com.cenedu.backend.domain.problem.dto.response.ProblemAnswerUnitResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemAssetResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemChoiceResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemContentBlockResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemLearningGuideResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemQuestionDetailResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemStepResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemStepSegmentResponse;
import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.repository.ProblemAnswerUnitRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAssetRepository;
import com.cenedu.backend.domain.problem.repository.ProblemChoiceRepository;
import com.cenedu.backend.domain.problem.repository.ProblemQuestionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemStepRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.QuestionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.ObjectProvider;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemQuestionDetailService {

    private final ProblemQuestionRepository problemQuestionRepository;
    private final ProblemChoiceRepository problemChoiceRepository;
    private final ProblemStepRepository problemStepRepository;
    private final ProblemAnswerUnitRepository problemAnswerUnitRepository;
    private final ProblemAssetRepository problemAssetRepository;
    private final CurriculumUnitQueryService curriculumUnitQueryService;
    private final ObjectMapper objectMapper;

    private final ObjectProvider<ProblemAssetUrlService> problemAssetUrlServiceProvider;

    // 선택된 여러 문항의 상세 데이터를 일괄 조회하여 원래 문항 순서대로 반환한다.
    public List<ProblemQuestionDetailResponse> getDetails(
        List<ProblemQuestion> questions
    ) {
        if (questions.isEmpty()) {
            return List.of();
        }

        List<Long> questionIds = questions.stream()
            .map(ProblemQuestion::getId)
            .toList();

        Set<Long> subUnitIds = questions.stream()
            .map(ProblemQuestion::getSubUnitId)
            .collect(Collectors.toCollection(HashSet::new));

        Map<Long, CurriculumPathResponse> curriculumBySubUnitId =
            curriculumUnitQueryService.getPathsBySubUnitIds(
                subUnitIds
            );

        Map<Long, List<ProblemChoiceResponse>> choicesByQuestionId =
            findChoicesByQuestionId(questionIds);

        Map<Long, List<ProblemStepResponse>> stepsByQuestionId =
            findStepsByQuestionId(questionIds);

        Map<Long, List<ProblemAnswerUnitResponse>> answerUnitsByQuestionId =
            findAnswerUnitsByQuestionId(questionIds);

        Map<Long, List<ProblemAssetResponse>> assetsByQuestionId =
            getAssetsByQuestionIds(questionIds);

        return questions.stream()
            .map(question -> ProblemQuestionDetailResponse.from(
                question,
                curriculumBySubUnitId.get(question.getSubUnitId()),
                parseContentBlocks(question.getContentBlocks()),
                assetsByQuestionId.getOrDefault(
                    question.getId(),
                    List.of()
                ),
                choicesByQuestionId.getOrDefault(
                    question.getId(),
                    List.of()
                ),
                stepsByQuestionId.getOrDefault(
                    question.getId(),
                    List.of()
                ),
                answerUnitsByQuestionId.getOrDefault(
                    question.getId(),
                    List.of()
                ),
                parseLearningGuide(question.getLearningGuide())
            ))
            .toList();
    }

    /** 문항 ID 목록으로 교사용 상세를 일괄 조회한다. */
    public List<ProblemQuestionDetailResponse> getDetailsByIds(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ProblemQuestion> questionsById = problemQuestionRepository
            .findAllById(questionIds)
            .stream()
            .collect(Collectors.toMap(ProblemQuestion::getId, question -> question));

        List<ProblemQuestion> orderedQuestions = questionIds.stream()
            .map(questionsById::get)
            .filter(question -> question != null)
            .toList();

        return getDetails(orderedQuestions);
    }

    /** 문항 ID별 유형을 반환한다. 존재하지 않는 ID는 결과에서 빠진다. */
    public Map<Long, QuestionType> getQuestionTypes(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }

        return problemQuestionRepository.findAllById(questionIds)
            .stream()
            .collect(Collectors.toMap(
                ProblemQuestion::getId,
                ProblemQuestion::getQuestionType,
                (first, second) -> first,
                LinkedHashMap::new
            ));
    }

    // 객관식 보기를 문항 ID별 응답 목록으로 묶는다.
    private Map<Long, List<ProblemChoiceResponse>>
    findChoicesByQuestionId(List<Long> questionIds) {

        return problemChoiceRepository
            .findAllByQuestionIds(questionIds)
            .stream()
            .collect(Collectors.groupingBy(
                choice -> choice.getQuestion().getId(),
                Collectors.mapping(
                    ProblemChoiceResponse::from,
                    Collectors.toList()
                )
            ));
    }

    // 풀이 단계의 JSON 세그먼트를 변환하고 문항 ID별로 묶는다.
    private Map<Long, List<ProblemStepResponse>>
    findStepsByQuestionId(List<Long> questionIds) {

        return problemStepRepository
            .findAllByQuestionIds(questionIds)
            .stream()
            .collect(Collectors.groupingBy(
                step -> step.getQuestion().getId(),
                Collectors.mapping(
                    step -> ProblemStepResponse.from(
                        step,
                        parseStepSegments(step.getSegments())
                    ),
                    Collectors.toList()
                )
            ));
    }

    // 정답 단위를 문항 ID별 응답 목록으로 묶는다.
    private Map<Long, List<ProblemAnswerUnitResponse>>
    findAnswerUnitsByQuestionId(List<Long> questionIds) {

        return problemAnswerUnitRepository
            .findAllByQuestionIds(questionIds)
            .stream()
            .collect(Collectors.groupingBy(
                answerUnit -> answerUnit.getQuestion().getId(),
                Collectors.mapping(
                    ProblemAnswerUnitResponse::from,
                    Collectors.toList()
                )
            ));
    }

    /**
     * 이미지 자산을 문항 ID별 응답 목록으로 묶는다. {@code url}은 조회 가능한 S3 URL이고,
     * S3 기능이 꺼져 있으면 {@code null}이다.
     *
     * <p>정답·해설이 섞이지 않은 자산 정보만 담으므로 학생·채점 응답에서도 그대로 쓴다.
     * 문항 상세({@link ProblemQuestionDetailResponse})는 정답을 포함하므로 재사용하면 안 된다.
     */
    public Map<Long, List<ProblemAssetResponse>>
    getAssetsByQuestionIds(List<Long> questionIds) {

        return problemAssetRepository
            .findAllByQuestionIds(questionIds)
            .stream()
            .collect(Collectors.groupingBy(
                asset -> asset.getQuestion().getId(),
                Collectors.mapping(
                    asset -> ProblemAssetResponse.from(
                        asset,
                        createAssetUrl(asset)
                    ),
                    Collectors.toList()
                )
            ));
    }

    /**
     * S3 기능이 활성화된 경우 storage_key를 조회 URL로 변환한다. 만들지 못하면 {@code null}이다.
     *
     * <p>버킷에 객체가 없을 때 던지지 않고 {@code null}로 떨어뜨린다. 이미지 한 장이 비었다고
     * 문항 목록 전체가 404가 되면 교사·학생이 그 화면을 아예 열지 못한다. 프론트는 url이 없으면
     * altText 플레이스홀더를 그리므로 나머지 문항은 정상적으로 풀 수 있다.
     *
     * <p>저장소 장애·설정 누락({@code IMAGE_STORAGE_FAILED}, {@code IMAGE_STORAGE_NOT_CONFIGURED})은
     * 삼키지 않는다. 그것까지 감추면 S3가 통째로 죽은 상황이 "이미지 없는 화면"으로 조용히 보인다.
     */
    private String createAssetUrl(ProblemAsset asset) {
        ProblemAssetUrlService urlService =
            problemAssetUrlServiceProvider.getIfAvailable();

        if (urlService == null) {
            return null;
        }

        if (asset.getStorageStatus()
                != com.cenedu.backend.domain.problem.entity.enums.ProblemAssetStorageStatus.READY) {
            return null;
        }

        try {
            return urlService.createUrl(asset);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.IMAGE_NOT_FOUND) {
                throw exception;
            }
            log.warn("문항 이미지가 버킷에 없어 URL 없이 내려보낸다 — questionId={}, assetKey={}, storageKey={}",
                asset.getQuestion().getId(), asset.getAssetKey(), asset.getStorageKey());
            return null;
        }
    }

    // 문항 본문의 JSON 배열을 화면 표시용 블록 목록으로 변환한다.
    private List<ProblemContentBlockResponse> parseContentBlocks(
        String contentBlocks
    ) {
        try {
            return objectMapper.readValue(
                contentBlocks,
                new TypeReference<
                    List<ProblemContentBlockResponse>
                    >() {
                }
            );
        } catch (JacksonException exception) {
            throw new BusinessException(
                ErrorCode.PROBLEM_DETAIL_DATA_INVALID
            );
        }
    }

    // 빈칸형 풀이 단계의 JSON 배열을 세그먼트 목록으로 변환한다.
    private List<ProblemStepSegmentResponse> parseStepSegments(
        String segments
    ) {
        try {
            return objectMapper.readValue(
                segments,
                new TypeReference<
                    List<ProblemStepSegmentResponse>
                    >() {
                }
            );
        } catch (JacksonException exception) {
            throw new BusinessException(
                ErrorCode.PROBLEM_DETAIL_DATA_INVALID
            );
        }
    }

    // 학습 가이드 JSON에서 화면에 필요한 제목·요약·핵심 내용만 추출한다.
    private ProblemLearningGuideResponse parseLearningGuide(
        String learningGuide
    ) {
        if (learningGuide == null || learningGuide.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(learningGuide);
            JsonNode keyPointsNode = root.path("keyPoints");

            List<String> keyPoints = new ArrayList<>();

            if (keyPointsNode.isArray()) {
                for (int index = 0; index < keyPointsNode.size(); index++) {
                    keyPoints.add(
                        keyPointsNode.get(index).asText()
                    );
                }
            }

            return ProblemLearningGuideResponse.of(
                root.path("conceptTitle").asText(null),
                root.path("summary").asText(null),
                keyPoints
            );
        } catch (JacksonException exception) {
            throw new BusinessException(
                ErrorCode.PROBLEM_DETAIL_DATA_INVALID
            );
        }
    }
}
