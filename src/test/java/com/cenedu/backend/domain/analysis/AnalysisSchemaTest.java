package com.cenedu.backend.domain.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.cenedu.backend.domain.analysis.entity.AnalysisAssessment;
import com.cenedu.backend.domain.analysis.entity.AnalysisAttempt;
import com.cenedu.backend.domain.analysis.entity.AnalysisReport;
import com.cenedu.backend.domain.analysis.entity.AssessmentStatus;
import com.cenedu.backend.domain.analysis.entity.AttemptPurpose;
import com.cenedu.backend.domain.analysis.entity.ReportStatus;
import com.cenedu.backend.domain.analysis.repository.AnalysisAssessmentRepository;
import com.cenedu.backend.domain.analysis.repository.AnalysisAttemptRepository;
import com.cenedu.backend.domain.analysis.repository.AnalysisReportRepository;

import com.cenedu.backend.support.PostgresTestcontainer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * analysis baseline 마이그레이션과 엔티티가 어긋나지 않는지 본다.
 *
 * <p>{@code ddl-auto: validate} 라서 둘이 어긋나면 컨텍스트가 아예 뜨지 않는다. 즉 이 테스트가
 * 통과한다는 것은 Flyway 가 만든 스키마를 Hibernate 가 인정했다는 뜻이다. 로컬 DB 를 쓰지 않고
 * 컨테이너를 새로 띄우므로, 남아 있던 스키마 때문에 통과하는 일이 없다.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
@DisplayName("analysis 스키마")
class AnalysisSchemaTest {

    @Autowired
    private AnalysisAssessmentRepository assessments;

    @Autowired
    private AnalysisAttemptRepository attempts;

    @Autowired
    private AnalysisReportRepository reports;

    @Test
    @DisplayName("회차를 저장하고 완료 처리하면 완료 시각이 한 번만 정해진다")
    void assessmentRoundTrip() {
        AnalysisAssessment saved = assessments.save(AnalysisAssessment.builder()
                .assessmentId("A-1").studentId("S-1")
                .assessmentTitle("1단원 형성평가")
                .assessmentDate(LocalDate.of(2026, 8, 7))
                .studentName("김학생").assessmentType("FORMATIVE")
                .simulation(false)
                .build());

        assertThat(saved.getStatus()).isEqualTo(AssessmentStatus.IN_PROGRESS);
        assertThat(saved.getCreatedAt()).isNotNull();

        saved.complete();
        Instant first = saved.getCompletedAt();
        saved.complete();

        assertThat(saved.completed()).isTrue();
        assertThat(saved.getCompletedAt()).isEqualTo(first);

        assertThat(assessments.findByAssessmentIdAndStudentId("A-1", "S-1")).isPresent();
    }

    @Test
    @DisplayName("풀이 시도의 jsonb 컬럼과 소수 정확도가 그대로 돌아온다")
    void attemptRoundTrip() {
        attempts.save(AnalysisAttempt.builder()
                .eventId("E-1").assessmentId("A-2").studentId("S-2")
                .problemNumber(3).problemId("P-1").problemTitle("일차방정식")
                .conceptId("C-1").stepId("STEP_2")
                .correct(false).hintUsed(true).submissionFailed(false)
                .evaluationArea("계산").topic("이항")
                // 백분율이다. 실데이터 범위(73.74~96.82)와 같은 자릿수를 쓴다.
                .referenceSuccessRate(new BigDecimal("83.01"))
                .difficultyBand("mid")
                .choicesJson("[\"1\",\"2\",\"3\"]")
                .responseType("choice")
                .studentAnswer("2").correctAnswer("3")
                .stepResponsesJson("[{\"stepId\":\"STEP_2\",\"value\":\"x=5\"}]")
                .occurredAt(Instant.parse("2026-08-07T01:00:00Z"))
                .build());

        AnalysisAttempt found = attempts.findByEventId("E-1").orElseThrow();

        assertThat(found.getChoicesJson()).contains("\"2\"");
        assertThat(found.getStepResponsesJson()).contains("STEP_2");
        assertThat(found.getReferenceSuccessRate()).isEqualByComparingTo("83.01");
        assertThat(found.getOccurredAt()).isEqualTo(Instant.parse("2026-08-07T01:00:00Z"));
        assertThat(found.isHintUsed()).isTrue();

        // 평가 영역과 풀이 단계는 다른 축이라 둘 다 살아 있어야 한다.
        assertThat(found.getEvaluationArea()).isEqualTo("계산");
        assertThat(found.getStepId()).isEqualTo("STEP_2");
    }

    @Test
    @DisplayName("목적을 밝히지 않은 응답은 진단으로 저장된다")
    void attemptDefaultsToDiagnostic() {
        attempts.save(AnalysisAttempt.builder()
                .eventId("E-3").assessmentId("A-4").studentId("S-4")
                .problemNumber(1)
                .occurredAt(Instant.parse("2026-08-07T03:00:00Z"))
                .build());

        // 제출 경로가 값을 채우지 않아도 지금까지와 같게 동작해야 한다.
        assertThat(attempts.findByEventId("E-3").orElseThrow().getPurpose())
                .isEqualTo(AttemptPurpose.DIAGNOSTIC);
    }

    @Test
    @DisplayName("응용 응답은 응용으로 저장된다")
    void attemptKeepsAppliedPurpose() {
        attempts.save(AnalysisAttempt.builder()
                .eventId("E-4").assessmentId("A-4").studentId("S-4")
                .problemNumber(2).purpose(AttemptPurpose.APPLIED)
                .occurredAt(Instant.parse("2026-08-07T04:00:00Z"))
                .build());

        assertThat(attempts.findByEventId("E-4").orElseThrow().getPurpose())
                .isEqualTo(AttemptPurpose.APPLIED);
    }

    @Test
    @DisplayName("빈 보기·단계 응답도 jsonb 기본값으로 저장된다")
    void attemptAllowsMissingJson() {
        attempts.save(AnalysisAttempt.builder()
                .eventId("E-2").assessmentId("A-2").studentId("S-2")
                .problemNumber(4)
                .occurredAt(Instant.parse("2026-08-07T02:00:00Z"))
                .build());

        AnalysisAttempt found = attempts.findByEventId("E-2").orElseThrow();

        assertThat(found.getChoicesJson()).isEqualTo("[]");
        assertThat(found.getStepResponsesJson()).isEqualTo("[]");
    }

    @Test
    @DisplayName("보고서는 초안을 남겨 둔 채 수정되고, 되돌리면 초안으로 돌아간다")
    void reportEditAndReset() {
        UUID reportId = UUID.randomUUID();
        AnalysisReport saved = reports.save(AnalysisReport.builder()
                .reportId(reportId).assessmentId("A-3").studentId("S-3")
                .reportType("STUDENT_DETAIL").statusName("보완 필요")
                .factsJson("{\"focus\":{\"statusName\":\"보완 필요\"}}")
                .narrativeJson("{\"summary\":\"초안\"}")
                .llmCallJson("{\"model\":\"gpt-4o-mini\"}")
                .sectionsJson("[{\"key\":\"summary\",\"body\":\"초안\"}]")
                .build());

        assertThat(saved.getReportStatus()).isEqualTo(ReportStatus.AI_DRAFT);

        saved.edit("STUDENT_DETAIL", "[{\"key\":\"summary\",\"body\":\"교사 수정\"}]");
        assertThat(saved.getReportStatus()).isEqualTo(ReportStatus.TEACHER_EDITED);
        assertThat(saved.getGeneratedSectionsJson()).contains("초안");

        saved.confirm("T-1");
        assertThat(saved.getConfirmedBy()).isEqualTo("T-1");

        saved.reset();
        assertThat(saved.getReportStatus()).isEqualTo(ReportStatus.AI_DRAFT);
        assertThat(saved.getEditedSectionsJson()).isEqualTo(saved.getGeneratedSectionsJson());
        assertThat(saved.getConfirmedBy()).isNull();

        assertThat(reports.findByReportId(reportId)).isPresent();
    }
}
