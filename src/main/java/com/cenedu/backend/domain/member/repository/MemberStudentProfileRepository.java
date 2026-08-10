package com.cenedu.backend.domain.member.repository;

import java.util.Optional;

import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

import org.springframework.data.jpa.repository.JpaRepository;

/** 학생 프로필 영속성 저장소. */
public interface MemberStudentProfileRepository extends JpaRepository<MemberStudentProfile, Long> {

    /** 학생 계정 ID로 학생 프로필을 조회한다. */
    Optional<MemberStudentProfile> findByUserId(Long userId);
}
