package com.cenedu.backend.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 개념 사이의 선수관계 1,098엣지. 방향은 concept_id -> prereq_concept_id (나중 -> 먼저)다.
 *
 * <p>{@code ChatConcept} 과 같은 이유로 {@link Immutable} 이고 정적 팩토리가 없다.
 * 양쪽 다 chat_concept.id 를 가리키지만 연관관계로 매핑하지 않았다 — 실제 확장은
 * {@code ChatConceptRepository.findPrereqClosure} 의 재귀 CTE 가 하고, 엔티티 그래프를
 * 타고 다니는 경로가 없기 때문이다.
 */
@Entity
@Getter
@Immutable
@Table(name = "chat_concept_prereq",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_concept_prereq",
                columnNames = {"concept_id", "prereq_concept_id"}),
        indexes = {
                @Index(name = "idx_chat_concept_prereq_concept", columnList = "concept_id"),
                @Index(name = "idx_chat_concept_prereq_prereq", columnList = "prereq_concept_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatConceptPrereq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 나중에 배우는 쪽. 이 개념을 이해하려면 prereqConceptId 를 먼저 알아야 한다. */
    @Column(name = "concept_id", nullable = false)
    private Long conceptId;

    /** 먼저 배워야 하는 쪽. 원천에 상호참조(A->B 와 B->A 동시 존재) 21쌍이 있어 그대로 두었다. */
    @Column(name = "prereq_concept_id", nullable = false)
    private Long prereqConceptId;
}
