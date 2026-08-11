package com.cenedu.backend.domain.member.repository;

import java.util.Optional;

import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

/** 학생 프로필 영속성 저장소. */
public interface MemberStudentProfileRepository
        extends JpaRepository<MemberStudentProfile, Long>,
        JpaSpecificationExecutor<MemberStudentProfile> {

    /** 학생 계정 ID로 학생 프로필을 조회한다. */
    Optional<MemberStudentProfile> findByUserId(Long userId);

    /** 조회 조건에 맞는 학생과 계정 정보를 페이지 단위로 조회한다. */
    @Override
    @EntityGraph(attributePaths = "user")
    Page<MemberStudentProfile> findAll(
            Specification<MemberStudentProfile> specification,
            Pageable pageable
    );
}
