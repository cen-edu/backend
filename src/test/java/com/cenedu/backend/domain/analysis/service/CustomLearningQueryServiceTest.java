package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.CustomLearningSessionListResponse;
import com.cenedu.backend.domain.analysis.entity.enums.CustomResolutionStatus;
import com.cenedu.backend.domain.analysis.repository.CustomLearningQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.CustomLearningSessionRow;
import com.cenedu.backend.global.common.enums.CustomStage;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomLearningQueryServiceTest {

    /** 원본 학습지. 차수 계산의 깊이 0 이다. */
    private static final long ROOT_WORKSHEET_ID = 100L;

    /** 원본을 부모로 갖는 맞춤 학습지라 1차가 된다. */
    private static final long CUSTOM_WORKSHEET_ID = 301L;

    private final AnalysisClassQueryService classQueryService =
            mock(AnalysisClassQueryService.class);
    private final CustomLearningQueryRepository repository =
            mock(CustomLearningQueryRepository.class);
    private final CustomLearningQueryService service =
            new CustomLearningQueryService(classQueryService, repository);

    @Test
    @DisplayName("응용 단계에 도달한 소분류와 회차를 해소로 분류한다")
    void groupsCustomLearningSession() {
        allowAssignment();
        when(repository.existsSourceAssignmentStudent(101L, 11L)).thenReturn(true);
        OffsetDateTime assignedAt = OffsetDateTime.parse("2026-08-14T10:00:00+09:00");
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-14T11:00:00+09:00");
        when(repository.findSessions(101L, 11L)).thenReturn(List.of(
                row(CustomStage.REVIEW, 1, 1, assignedAt, completedAt),
                row(CustomStage.SIMILAR, 1, 1, assignedAt, completedAt),
                row(CustomStage.ADVANCED, 1, 1, assignedAt, completedAt)));

        CustomLearningSessionListResponse response = service.getSessions(7L, 101L, 11L);

        assertThat(response.sessions()).hasSize(1);
        CustomLearningSessionListResponse.CustomLearningSession session =
                response.sessions().getFirst();
        assertThat(session.customAssignmentId()).isEqualTo(201L);
        assertThat(session.overallResolutionStatus())
                .isEqualTo(CustomResolutionStatus.RESOLVED);
        assertThat(session.subcategories()).hasSize(1);
        CustomLearningSessionListResponse.CustomSubcategoryResult subcategory =
                session.subcategories().getFirst();
        assertThat(subcategory.resolutionStatus())
                .isEqualTo(CustomResolutionStatus.RESOLVED);
        assertThat(subcategory.stages()).extracting(
                        CustomLearningSessionListResponse.CustomStageResult::stage)
                .containsExactly(CustomStage.REVIEW, CustomStage.SIMILAR, CustomStage.ADVANCED);
    }

    @Test
    @DisplayName("진단 문항이 남아 있으면 진행 중으로 분류하고 없는 단계는 0건으로 채운다")
    void fillsMissingStagesAndMarksIncompleteSessionInProgress() {
        allowAssignment();
        when(repository.existsSourceAssignmentStudent(101L, 11L)).thenReturn(true);
        OffsetDateTime assignedAt = OffsetDateTime.parse("2026-08-14T10:00:00+09:00");
        when(repository.findSessions(101L, 11L)).thenReturn(List.of(
                row(CustomStage.REVIEW, 1, 1, assignedAt, null,
                        1, 3, 1, 3, 1, 2)));

        CustomLearningSessionListResponse.CustomLearningSession session = service
                .getSessions(7L, 101L, 11L)
                .sessions()
                .getFirst();

        assertThat(session.overallResolutionStatus())
                .isEqualTo(CustomResolutionStatus.IN_PROGRESS);
        assertThat(session.subcategories().getFirst().resolutionStatus())
                .isEqualTo(CustomResolutionStatus.IN_PROGRESS);
        assertThat(session.subcategories().getFirst().stages().get(1).correctCount())
                .isZero();
        assertThat(session.subcategories().getFirst().stages().get(1).totalCount())
                .isZero();
    }

    @Test
    @DisplayName("진단을 완료했지만 응용 단계에 도달하지 못하면 미해소로 분류한다")
    void marksCompletedDiagnosticWithoutAppliedStageUnresolved() {
        allowAssignment();
        when(repository.existsSourceAssignmentStudent(101L, 11L)).thenReturn(true);
        OffsetDateTime assignedAt = OffsetDateTime.parse("2026-08-14T10:00:00+09:00");
        when(repository.findSessions(101L, 11L)).thenReturn(List.of(
                row(CustomStage.REVIEW, 1, 1, assignedAt, null,
                        2, 2, 2, 2, 1, 2),
                row(CustomStage.SIMILAR, 0, 1, assignedAt, null,
                        2, 2, 2, 2, 1, 2)));

        CustomLearningSessionListResponse.CustomLearningSession session = service
                .getSessions(7L, 101L, 11L)
                .sessions()
                .getFirst();

        assertThat(session.overallResolutionStatus())
                .isEqualTo(CustomResolutionStatus.UNRESOLVED);
        assertThat(session.subcategories().getFirst().resolutionStatus())
                .isEqualTo(CustomResolutionStatus.UNRESOLVED);
    }

    @Test
    @DisplayName("상 난이도 진단 문항을 모두 맞히면 응용 출제 가능 상태로 보고 해소한다")
    void marksClearedHighDifficultyDiagnosticResolved() {
        allowAssignment();
        when(repository.existsSourceAssignmentStudent(101L, 11L)).thenReturn(true);
        OffsetDateTime assignedAt = OffsetDateTime.parse("2026-08-14T10:00:00+09:00");
        when(repository.findSessions(101L, 11L)).thenReturn(List.of(
                row(CustomStage.REVIEW, 2, 2, assignedAt, null,
                        2, 2, 2, 2, 2, 3)));

        CustomLearningSessionListResponse.CustomSubcategoryResult subcategory = service
                .getSessions(7L, 101L, 11L)
                .sessions()
                .getFirst()
                .subcategories()
                .getFirst();

        assertThat(subcategory.resolutionStatus())
                .isEqualTo(CustomResolutionStatus.RESOLVED);
    }

    @Test
    @DisplayName("모든 소분류가 해소되어야 회차 전체를 해소로 분류한다")
    void requiresEverySubcategoryToBeResolved() {
        allowAssignment();
        when(repository.existsSourceAssignmentStudent(101L, 11L)).thenReturn(true);
        OffsetDateTime assignedAt = OffsetDateTime.parse("2026-08-14T10:00:00+09:00");
        when(repository.findSessions(101L, 11L)).thenReturn(List.of(
                row(301L, "소인수분해", CustomStage.ADVANCED, 0, 1,
                        assignedAt, 2, 2, 2, 2, 1, 2),
                row(302L, "최대공약수", CustomStage.SIMILAR, 1, 1,
                        assignedAt, 2, 2, 2, 2, 1, 2)));

        CustomLearningSessionListResponse.CustomLearningSession session = service
                .getSessions(7L, 101L, 11L)
                .sessions()
                .getFirst();

        assertThat(session.subcategories()).extracting(
                        CustomLearningSessionListResponse.CustomSubcategoryResult::resolutionStatus)
                .containsExactly(
                        CustomResolutionStatus.RESOLVED,
                        CustomResolutionStatus.UNRESOLVED);
        assertThat(session.overallResolutionStatus())
                .isEqualTo(CustomResolutionStatus.UNRESOLVED);
    }

    @Test
    @DisplayName("차수는 학습지 계보의 깊이로 매기고 오름차순으로 내보낸다")
    void numbersSessionsByLineageDepth() {
        allowAssignment();
        when(repository.existsSourceAssignmentStudent(101L, 11L)).thenReturn(true);
        OffsetDateTime firstAssignedAt = OffsetDateTime.parse("2026-08-13T09:00:00+09:00");
        OffsetDateTime secondAssignedAt = OffsetDateTime.parse("2026-08-15T09:00:00+09:00");
        // 2차를 먼저 넣어, 정렬이 조회 순서가 아니라 차수를 따르는지 본다.
        when(repository.findSessions(101L, 11L)).thenReturn(List.of(
                lineageRow(202L, 302L, CUSTOM_WORKSHEET_ID, secondAssignedAt),
                lineageRow(201L, CUSTOM_WORKSHEET_ID, ROOT_WORKSHEET_ID, firstAssignedAt)));

        CustomLearningSessionListResponse response = service.getSessions(7L, 101L, 11L);

        assertThat(response.sessions()).extracting(
                        CustomLearningSessionListResponse.CustomLearningSession::sessionNumber,
                        CustomLearningSessionListResponse.CustomLearningSession::customAssignmentId)
                .containsExactly(tuple(1, 201L), tuple(2, 202L));
    }

    @Test
    @DisplayName("계보가 끊긴 회차는 1차인 척하지 않고 차수 미상으로 맨 뒤에 둔다")
    void placesUnnumberedSessionsLast() {
        allowAssignment();
        when(repository.existsSourceAssignmentStudent(101L, 11L)).thenReturn(true);
        OffsetDateTime assignedAt = OffsetDateTime.parse("2026-08-13T09:00:00+09:00");
        when(repository.findSessions(101L, 11L)).thenReturn(List.of(
                lineageRow(202L, 302L, null, assignedAt),
                lineageRow(201L, CUSTOM_WORKSHEET_ID, ROOT_WORKSHEET_ID, assignedAt)));

        CustomLearningSessionListResponse response = service.getSessions(7L, 101L, 11L);

        assertThat(response.sessions()).extracting(
                        CustomLearningSessionListResponse.CustomLearningSession::sessionNumber,
                        CustomLearningSessionListResponse.CustomLearningSession::customAssignmentId)
                .containsExactly(tuple(1, 201L), tuple(0, 202L));
    }

    /** 차수 계산에 필요한 계보만 다르게 준 행. 나머지 값은 판정에 영향이 없다. */
    private CustomLearningSessionRow lineageRow(
            long customAssignmentId,
            long worksheetId,
            Long parentWorksheetId,
            OffsetDateTime assignedAt
    ) {
        return new CustomLearningSessionRow(
                customAssignmentId, worksheetId, parentWorksheetId, ROOT_WORKSHEET_ID,
                assignedAt, assignedAt, 1, 1, new BigDecimal("100.0"),
                1L, "소인수분해", 2, 1, 1,
                new BigDecimal("100.0"), new BigDecimal("100.0"), 1, 1, 1,
                CustomStage.SIMILAR, 1, 1);
    }

    @Test
    @DisplayName("맞춤 학습 기록이 없으면 빈 목록을 반환한다")
    void returnsEmptySessions() {
        allowAssignment();
        when(repository.existsSourceAssignmentStudent(101L, 11L)).thenReturn(true);
        when(repository.findSessions(101L, 11L)).thenReturn(List.of());

        CustomLearningSessionListResponse response = service.getSessions(7L, 101L, 11L);

        assertThat(response.sessions()).isEmpty();
    }

    @Test
    @DisplayName("원본 학습지를 배정받지 않은 학생의 맞춤 결과 조회는 거부한다")
    void rejectsStudentNotAssigned() {
        allowAssignment();
        when(repository.existsSourceAssignmentStudent(101L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getSessions(7L, 101L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_STUDENT_NOT_ASSIGNED);
    }

    private CustomLearningSessionRow row(
            CustomStage stage,
            int correctCount,
            int totalCount,
            OffsetDateTime assignedAt,
            OffsetDateTime completedAt
    ) {
        return row(stage, correctCount, totalCount, assignedAt, completedAt,
                3, 3, 2, 2, 1, 2);
    }

    private CustomLearningSessionRow row(
            CustomStage stage,
            int correctCount,
            int totalCount,
            OffsetDateTime assignedAt,
            OffsetDateTime completedAt,
            int sessionCompletedItemCount,
            int sessionTotalItemCount,
            int diagnosticCompletedItemCount,
            int diagnosticTotalItemCount,
            int diagnosticCorrectItemCount,
            int currentDifficulty
    ) {
        return row(301L, "소인수분해", stage, correctCount, totalCount,
                assignedAt, sessionCompletedItemCount, sessionTotalItemCount,
                diagnosticCompletedItemCount, diagnosticTotalItemCount,
                diagnosticCorrectItemCount, currentDifficulty, completedAt);
    }

    private CustomLearningSessionRow row(
            long subcategoryId,
            String subcategoryName,
            CustomStage stage,
            int correctCount,
            int totalCount,
            OffsetDateTime assignedAt,
            int sessionCompletedItemCount,
            int sessionTotalItemCount,
            int diagnosticCompletedItemCount,
            int diagnosticTotalItemCount,
            int diagnosticCorrectItemCount,
            int currentDifficulty
    ) {
        return row(subcategoryId, subcategoryName, stage, correctCount, totalCount,
                assignedAt, sessionCompletedItemCount, sessionTotalItemCount,
                diagnosticCompletedItemCount, diagnosticTotalItemCount,
                diagnosticCorrectItemCount, currentDifficulty, null);
    }

    private CustomLearningSessionRow row(
            long subcategoryId,
            String subcategoryName,
            CustomStage stage,
            int correctCount,
            int totalCount,
            OffsetDateTime assignedAt,
            int sessionCompletedItemCount,
            int sessionTotalItemCount,
            int diagnosticCompletedItemCount,
            int diagnosticTotalItemCount,
            int diagnosticCorrectItemCount,
            int currentDifficulty,
            OffsetDateTime completedAt
    ) {
        return new CustomLearningSessionRow(
                201L,
                CUSTOM_WORKSHEET_ID,
                ROOT_WORKSHEET_ID,
                ROOT_WORKSHEET_ID,
                assignedAt,
                completedAt,
                sessionCompletedItemCount,
                sessionTotalItemCount,
                new BigDecimal("33.3"),
                subcategoryId,
                subcategoryName,
                currentDifficulty,
                sessionCompletedItemCount,
                sessionTotalItemCount,
                new BigDecimal("100.0"),
                new BigDecimal("33.3"),
                diagnosticCompletedItemCount,
                diagnosticTotalItemCount,
                diagnosticCorrectItemCount,
                stage,
                correctCount,
                totalCount);
    }

    private void allowAssignment() {
        when(classQueryService.getAuthorizedAssignment(7L, 101L))
                .thenReturn(new AnalysisAssignmentAccessRow(
                        101L,
                        "학습평가",
                        WorksheetType.GENERAL_LEARNING,
                        "1반",
                        7L,
                        7L));
    }
}
