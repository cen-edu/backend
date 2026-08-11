package com.cenedu.backend.domain.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문항 주제 마스터. 소단원과 함수 관계다.
 *
 * <p>PK 가 원천 데이터의 주제 코드(자연키)라 bigserial 원칙의 유일한 예외다.
 */
@Entity
@Getter
@Table(name = "problem_topic",
        indexes = @Index(name = "idx_problem_topic_sub_unit", columnList = "sub_unit_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemTopic {

    @Id
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** curriculum 도메인의 소단원 ID. */
    @Column(name = "sub_unit_id", nullable = false)
    private Long subUnitId;

    private ProblemTopic(String code, String name, Long subUnitId) {
        this.code = code;
        this.name = name;
        this.subUnitId = subUnitId;
    }

    /** 원천 주제 코드를 식별자로 삼아 주제를 생성한다. */
    public static ProblemTopic create(String code, String name, Long subUnitId) {
        return new ProblemTopic(code, name, subUnitId);
    }
}
