package com.cenedu.backend.domain.curriculum.entity;

import com.cenedu.backend.domain.curriculum.entity.enums.UnitLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대단원>중단원>소단원 자기참조 트리. 초기 시드 후 조회 전용이라 시각 컬럼이 없다. */
@Entity
@Getter
@Table(name = "curriculum_unit",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_curriculum_unit_external_key", columnNames = "external_key"),
        indexes = @Index(name = "idx_curriculum_unit_parent", columnList = "parent_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurriculumUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id",
            foreignKey = @ForeignKey(name = "fk_curriculum_unit_parent"))
    private CurriculumUnit parent;

    @Column(name = "external_key", nullable = false, length = 255)
    private String externalKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_level", nullable = false, length = 12)
    private UnitLevel unitLevel;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "grade", nullable = false)
    private short grade;

    /** 계층 조회와 학기 필터링을 위해 모든 계층에 동일한 학기를 저장한다. */
    @Column(name = "semester")
    private Short semester;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private CurriculumUnit(CurriculumUnit parent, String externalKey, UnitLevel unitLevel,
                           String name, short grade, Short semester, int displayOrder) {
        this.parent = parent;
        this.externalKey = externalKey;
        this.unitLevel = unitLevel;
        this.name = name;
        this.grade = grade;
        this.semester = semester;
        this.displayOrder = displayOrder;
    }

    /** 단원 트리의 노드를 생성한다. 최상위 대단원은 parent 가 null 이다. */
    public static CurriculumUnit create(CurriculumUnit parent, String externalKey,
                                        UnitLevel unitLevel, String name, short grade,
                                        Short semester, int displayOrder) {
        return new CurriculumUnit(parent, externalKey, unitLevel, name, grade, semester,
                displayOrder);
    }
}
