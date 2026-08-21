package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.entity.ProblemTeacherDecisionEvent;
import com.cenedu.backend.domain.problem.repository.ProblemTeacherDecisionEventRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

class ProblemTeacherDecisionEventServiceTest {
    @Test
    void modificationEventStoresOnlyStructuredEnumsAndIsIdempotent() {
        var repository = mock(ProblemTeacherDecisionEventRepository.class);
        when(repository.existsByEventKey(any())).thenReturn(false, true);
        var service = new ProblemTeacherDecisionEventService(repository);
        List<ProblemEditInstruction> instructions = List.of(new ProblemEditInstruction(
                EditTargetType.CHOICE, "C1", EditChangeNature.SEMANTIC, "TEACHER_PROMPT_SENTINEL"));
        UUID requestId = UUID.randomUUID();
        service.recordModificationStarted(7L, 30L, 41L, requestId, instructions);
        service.recordModificationStarted(7L, 30L, 41L, requestId, instructions);
        ArgumentCaptor<ProblemTeacherDecisionEvent> captor = ArgumentCaptor.forClass(ProblemTeacherDecisionEvent.class);
        verify(repository, times(1)).save(captor.capture());
        ProblemTeacherDecisionEvent event = captor.getValue();
        assertThat(event.getChangeNaturesJson()).isEqualTo("[\"SEMANTIC\"]");
        assertThat(event.getTargetTypesJson()).isEqualTo("[\"CHOICE\"]");
        assertThat(event.getChangeNaturesJson() + event.getTargetTypesJson()).doesNotContain("TEACHER_PROMPT_SENTINEL");
    }

    @Test
    void jsonb_event_fields_use_hibernate_json_jdbc_type() throws Exception {
        var eventType = ProblemTeacherDecisionEvent.class;
        var changeNatures = eventType.getDeclaredField("changeNaturesJson");
        var targetTypes = eventType.getDeclaredField("targetTypesJson");

        assertThat(changeNatures.getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.JSON);
        assertThat(targetTypes.getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.JSON);
    }
}
