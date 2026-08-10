package com.cenedu.backend.domain.member.repository;

import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

import org.springframework.data.jpa.repository.JpaRepository;

/** 학생 프로필 영속성 저장소. */
public interface MemberStudentProfileRepository extends JpaRepository<MemberStudentProfile, Long> {
}
