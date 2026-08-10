package com.cenedu.backend.domain.member.repository;

import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.member.entity.MemberSchoolClass;

import org.springframework.data.jpa.repository.JpaRepository;

/** 학년도 단위 반 영속성 저장소. */
public interface MemberSchoolClassRepository extends JpaRepository<MemberSchoolClass, Long> {

    /** 교사가 소유한 삭제되지 않은 반을 표시 순서대로 조회한다. */
    List<MemberSchoolClass> findAllByHomeroomTeacherIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            Long teacherId);

    /** 반 ID로 삭제되지 않은 반을 조회한다. */
    Optional<MemberSchoolClass> findByIdAndDeletedAtIsNull(Long id);

    /** 교사가 소유한 마지막 표시 순서의 반을 조회한다. */
    Optional<MemberSchoolClass> findTopByHomeroomTeacherIdAndDeletedAtIsNullOrderByDisplayOrderDesc(
            Long teacherId);
}
