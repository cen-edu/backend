package com.cenedu.backend.domain.chat.entity;

import com.cenedu.backend.domain.chat.entity.enums.GradeBand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 개념 마스터 455행. 개념 챗봇이 LLM 에 주입하는 본문의 출처다.
 *
 * <p>V20260812_0900 이 만들고 V20260812_0930 이 채운다. 자바 코드가 행을 만들거나 고치는
 * 경로가 없어 정적 팩토리를 두지 않았고, 실수로 {@code save()} 를 불러도 UPDATE 가 나가지
 * 않도록 {@link Immutable} 을 붙였다. 내용을 바꾸려면 새 마이그레이션을 쓴다.
 * 같은 이유로 시각 컬럼과 소프트 삭제가 없어 BaseTimeEntity 를 상속하지 않는다.
 */
@Entity
@Getter
@Immutable
@Table(name = "chat_concept",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_concept_source_id", columnNames = "source_concept_id"),
        indexes = {
                @Index(name = "idx_chat_concept_sub_unit", columnList = "sub_unit_id"),
                @Index(name = "idx_chat_concept_name", columnList = "name")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatConcept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * AI Hub 133번 원천의 concept id.
     *
     * <p>시드를 다시 만들었을 때 대조하는 유일한 축이다. name 은 원천에서 81개가 학년별로
     * 중복되므로(거듭제곱이 중1·중2·고등에 각각 있다) 대조나 조인에 쓰면 안 된다.
     */
    @Column(name = "source_concept_id", nullable = false)
    private Integer sourceConceptId;

    /** 개념 이름. 고유하지 않다 — 원천에서 81개가 학년별로 중복된다. */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * 소속 소단원. curriculum_unit.id 를 가리키지만 연관관계로 매핑하지 않았다.
     *
     * <p>{@code CurriculumUnit} 이 curriculum 도메인 소유라 결합을 늘리지 않는 편이 낫고,
     * 이 테이블의 조회는 sub_unit_id 로 거르거나 재귀 CTE 를 타는 것뿐이라 단원 객체를 실제로
     * 꺼내 쓰는 경로가 없다. 필요해지면 {@code @ManyToOne} 으로 바꾸는 데 컬럼 변경은 없다.
     *
     * <p>초등 개념은 NULL 이다.
     */
    @Column(name = "sub_unit_id")
    private Long subUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_band", nullable = false, length = 12)
    private GradeBand gradeBand;

    /** 원천 학기 표기. 예: '초등-초5-1학기'. 프롬프트에서 "이건 초5 때 배운 것"을 알리는 데 쓴다. */
    @Column(name = "source_semester", length = 30)
    private String sourceSemester;

    /**
     * 중1 개념에서 선수 방향으로 몇 걸음 떨어진 초등 개념인지. 초등 행에만 값이 있다.
     *
     * <p>455행을 전부 적재하되 주입 기본 정책은 1 이하다. 학년과는 다른 축이라, 같은 초2
     * 개념이라도 hop 이 1인 것과 2인 것이 갈린다.
     */
    @Column(name = "elem_hop")
    private Short elemHop;

    /**
     * LLM 에 주입하는 개념 설명 본문. 원천 문자열 그대로이며 가공·요약이 없다.
     *
     * <p>242행이 줄바꿈을 실제 개행이 아니라 리터럴 두 글자 {@code \n} 으로 갖고 있어
     * 프롬프트를 조립하기 직전에 치환해야 한다. LaTeX 를 포함한 행이 127건이므로 DB 에서는
     * 어떤 변형도 하지 않고 그대로 들고 있는다.
     */
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /** 2015 성취기준 '문장'. 코드가 아니다 — 원천에 코드가 없다. 빈 문자열 1건(각기둥). */
    @Column(name = "achievement_2015", columnDefinition = "text")
    private String achievement2015;

    /** 2022 개정으로 학년이 이동한 개념. 4건(평균·대푯값·중앙값·최빈값)이 중3에서 중1로 내려왔다. */
    @Column(name = "moved_from_grade", nullable = false)
    private boolean movedFromGrade;
}
