package com.cenedu.backend.domain.member.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

/** 학생 프로필 영속성 저장소. */
public interface MemberStudentProfileRepository
        extends JpaRepository<MemberStudentProfile, Long>,
        JpaSpecificationExecutor<MemberStudentProfile> {

    /** 학생 계정 ID로 학생 프로필을 조회한다. */
    Optional<MemberStudentProfile> findByUserId(Long userId);

    /** 교사 소유의 활성 학생 중 지정 학년 학생을 이름순으로 모두 조회한다. */
    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT profile
            FROM MemberStudentProfile profile
            WHERE profile.ownerTeacher.id = :teacherId
              AND profile.grade = :grade
              AND profile.user.deletedAt IS NULL
            ORDER BY profile.user.name ASC, profile.userId ASC
            """)
    List<MemberStudentProfile> findAllClassCandidates(
            @Param("teacherId") Long teacherId,
            @Param("grade") short grade);

    /** 교사 소유의 활성 학생 중 지정 학년과 이름 검색어에 맞는 학생을 모두 조회한다. */
    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT profile
            FROM MemberStudentProfile profile
            WHERE profile.ownerTeacher.id = :teacherId
              AND profile.grade = :grade
              AND profile.user.deletedAt IS NULL
              AND LOCATE(LOWER(:keyword), LOWER(profile.user.name)) > 0
            ORDER BY profile.user.name ASC, profile.userId ASC
            """)
    List<MemberStudentProfile> findAllClassCandidatesByKeyword(
            @Param("teacherId") Long teacherId,
            @Param("grade") short grade,
            @Param("keyword") String keyword);

    /** 지정된 학생 계정 ID들의 학생 프로필과 계정 정보를 함께 조회한다. */
    @EntityGraph(attributePaths = {"user", "ownerTeacher"})
    List<MemberStudentProfile> findAllByUserIdIn(Collection<Long> userIds);

    /** 조회 조건에 맞는 학생과 계정 정보를 페이지 단위로 조회한다. */
    @Override
    @EntityGraph(attributePaths = "user")
    Page<MemberStudentProfile> findAll(
            Specification<MemberStudentProfile> specification,
            Pageable pageable
    );
}
