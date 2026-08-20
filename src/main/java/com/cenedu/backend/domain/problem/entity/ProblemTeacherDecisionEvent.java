package com.cenedu.backend.domain.problem.entity;

import com.cenedu.backend.domain.problem.entity.enums.TeacherDecisionType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 교사의 성공한 저작 상태 결정을 append-only로 보관하는 품질 이벤트다. */
@Entity
@Getter
@Table(name = "problem_teacher_decision_event", uniqueConstraints = @UniqueConstraint(name = "uk_problem_teacher_decision_event_key", columnNames = "event_key"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemTeacherDecisionEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_key", nullable = false, updatable = false, length = 180) private String eventKey;
    @Column(name = "teacher_id", nullable = false, updatable = false) private Long teacherId;
    @Column(name = "session_id", updatable = false) private Long sessionId;
    @Column(name = "version_id", updatable = false) private Long versionId;
    @Enumerated(EnumType.STRING) @Column(name = "decision_type", nullable = false, updatable = false, length = 30) private TeacherDecisionType decisionType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "change_natures", columnDefinition = "jsonb", updatable = false) private String changeNaturesJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_types", columnDefinition = "jsonb", updatable = false) private String targetTypesJson;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    private ProblemTeacherDecisionEvent(String eventKey, long teacherId, Long sessionId, Long versionId,
                                        TeacherDecisionType decisionType, String changeNaturesJson, String targetTypesJson) {
        this.eventKey = eventKey; this.teacherId = teacherId; this.sessionId = sessionId; this.versionId = versionId;
        this.decisionType = decisionType; this.changeNaturesJson = changeNaturesJson; this.targetTypesJson = targetTypesJson;
        this.createdAt = LocalDateTime.now();
    }

    /** 변경할 수 없는 교사 결정을 생성한다. */
    public static ProblemTeacherDecisionEvent create(String eventKey, long teacherId, Long sessionId, Long versionId,
                                                     TeacherDecisionType type, String changeNaturesJson, String targetTypesJson) {
        return new ProblemTeacherDecisionEvent(eventKey, teacherId, sessionId, versionId, type, changeNaturesJson, targetTypesJson);
    }
}
