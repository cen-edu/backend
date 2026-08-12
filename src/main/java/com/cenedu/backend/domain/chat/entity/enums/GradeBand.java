package com.cenedu.backend.domain.chat.entity.enums;

/**
 * 개념이 속한 학년대. chat_concept.grade_band 의 DB 값과 이름이 일치한다.
 *
 * <p>이 값이 같은 행의 다른 두 컬럼을 반대로 강제한다. DDL 의
 * {@code ck_chat_concept_band_shape} 가 {@code MIDDLE_1} 이면 sub_unit_id 가 NOT NULL 이고
 * elem_hop 이 NULL, {@code ELEMENTARY} 면 그 반대여야 한다고 못 박고 있다.
 * 초등 개념은 EBS 단원 트리 밖이라 소단원에 매달 수 없고, 대신 중1 개념에서 몇 걸음
 * 떨어졌는지를 elem_hop 으로 갖는다.
 */
public enum GradeBand {
    MIDDLE_1,
    ELEMENTARY
}
