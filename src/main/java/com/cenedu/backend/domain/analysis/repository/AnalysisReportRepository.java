package com.cenedu.backend.domain.analysis.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.entity.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 분석 보고서를 저장하고 생성 상태를 전이시키는 analysis 소유 Repository. */
public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    /** 학생 학습지 수행 회차의 보고서를 조회한다. */
    Optional<AnalysisReport> findByAssignmentStudentId(long assignmentStudentId);

    /**
     * 보고서를 생성 중 상태로 바꾸고, 바꿀 수 있었는지를 갱신 행 수로 알려준다.
     *
     * <p>반환값이 1이면 이 호출이 생성 작업을 맡았다는 뜻이고, 0이면 다른 요청이 이미 생성 중이라
     * 아무것도 하지 않아야 한다. 교사가 버튼을 두 번 누르거나 두 탭에서 화면을 열어도 LLM이 두 번
     * 돌지 않게 하는 장치다. 조회 후 갱신으로 나누면 그 사이에 다른 요청이 끼어들 수 있어 한 문장으로 둔다.
     *
     * <p>{@code staleCutoff} 는 생성 중인 채로 멈춘 행을 되살리기 위한 기준선이다. 프로세스가 죽으면
     * 상태가 GENERATING 에 남아 아무도 다시 시작할 수 없게 되므로, 그보다 오래된 행은 다시 맡는다.
     * 두 값 모두 SQL 의 {@code now()} 가 아니라 애플리케이션이 계산해 넘긴다 — {@code updated_at} 은
     * 시간대가 없는 timestamp 라서 timestamptz 인 {@code now()} 와 비교하면 세션 시간대에 따라 어긋난다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO analysis_report (
                assignment_student_id, generation_status, created_at, updated_at
            )
            VALUES (:assignmentStudentId, 'GENERATING', :now, :now)
            ON CONFLICT (assignment_student_id) DO UPDATE
               SET generation_status = 'GENERATING', updated_at = :now
             WHERE analysis_report.generation_status <> 'GENERATING'
                OR analysis_report.updated_at < :staleCutoff
            """, nativeQuery = true)
    int startGeneration(
            @Param("assignmentStudentId") long assignmentStudentId,
            @Param("now") LocalDateTime now,
            @Param("staleCutoff") LocalDateTime staleCutoff);
}
