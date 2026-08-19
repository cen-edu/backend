package com.cenedu.backend.domain.worksheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.member.service.SchoolClassService;
import com.cenedu.backend.domain.problem.dto.response.FinalizedProblemReferenceResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemDeploymentStatus;
import com.cenedu.backend.domain.problem.service.ProblemAuthoringFinalizationService;
import com.cenedu.backend.domain.problem.service.ProblemQuestionDetailService;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetCreateRequest;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetGenSpecRequest;
import com.cenedu.backend.domain.worksheet.dto.request.WorksheetItemRequest;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetAssignmentStudentRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetGenSpecRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetItemRepository;
import com.cenedu.backend.domain.worksheet.repository.WorksheetRepository;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorksheetCommandServiceAuthoringTest {

    @Mock private WorksheetRepository worksheetRepository;
    @Mock private WorksheetGenSpecRepository worksheetGenSpecRepository;
    @Mock private WorksheetItemRepository worksheetItemRepository;
    @Mock private WorksheetAssignmentRepository worksheetAssignmentRepository;
    @Mock private WorksheetAssignmentStudentRepository worksheetAssignmentStudentRepository;
    @Mock private ProblemQuestionDetailService problemQuestionDetailService;
    @Mock private ProblemAuthoringFinalizationService problemAuthoringFinalizationService;
    @Mock private SchoolClassService schoolClassService;

    private WorksheetCommandService service;

    @BeforeEach
    void setUp() {
        service = new WorksheetCommandService(worksheetRepository, worksheetGenSpecRepository,
                worksheetItemRepository, worksheetAssignmentRepository,
                worksheetAssignmentStudentRepository, problemQuestionDetailService,
                problemAuthoringFinalizationService, schoolClassService);
    }

    @Test
    void authoringSession을_최종화한_questionId로_worksheetItem을_저장한다() {
        when(problemAuthoringFinalizationService.finalizeForWorksheet(7L, List.of(31L)))
                .thenReturn(List.of(new FinalizedProblemReferenceResponse(
                        31L, 41L, 51L, QuestionType.STEP_FILL,
                        ProblemDeploymentStatus.READY)));
        when(problemQuestionDetailService.getQuestionTypes(List.of(51L)))
                .thenReturn(Map.of(51L, QuestionType.STEP_FILL));

        service.createWorksheet(7L, request(new WorksheetItemRequest(
                null, 31L, 1, null, null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WorksheetItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(worksheetItemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .extracting(WorksheetItem::getQuestionId)
                .isEqualTo(51L);
    }

    @Test
    void 기존_questionId는_최종화_없이_그대로_저장한다() {
        when(problemAuthoringFinalizationService.finalizeForWorksheet(7L, List.of()))
                .thenReturn(List.of());
        when(problemQuestionDetailService.getQuestionTypes(List.of(51L)))
                .thenReturn(Map.of(51L, QuestionType.STEP_FILL));

        service.createWorksheet(7L, request(new WorksheetItemRequest(
                51L, null, 1, null, null)));

        verify(problemAuthoringFinalizationService).finalizeForWorksheet(7L, List.of());
        verify(worksheetItemRepository).saveAll(anyList());
    }

    private WorksheetCreateRequest request(WorksheetItemRequest item) {
        return new WorksheetCreateRequest("AI 일반학습", "practice", "manual", 1,
                "first", null,
                List.of(new WorksheetGenSpecRequest(101L, "step", "mid", 1)),
                List.of(item));
    }
}
