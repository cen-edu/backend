package com.cenedu.backend.domain.member.repository;

import java.util.Optional;

import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 학생 프로필 영속성 저장소. */
public interface MemberStudentProfileRepository extends JpaRepository<MemberStudentProfile, Long> {

    /** 학생 계정 ID로 학생 프로필을 조회한다. */
    Optional<MemberStudentProfile> findByUserId(Long userId);

    /** 교사가 소유한 활성 학생을 등록연도, 학년, 반, 이름 조건으로 조회한다. */
    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT profile
            FROM MemberStudentProfile profile
            WHERE profile.ownerTeacher.id = :teacherId
              AND profile.user.deletedAt IS NULL
              AND (:registrationYear IS NULL
                   OR profile.registrationYear = :registrationYear)
              AND (:grade IS NULL OR profile.grade = :grade)
              AND (:keyword IS NULL OR LOWER(profile.user.name) LIKE CONCAT('%', :keyword, '%'))
              AND (:classId IS NULL OR EXISTS (
                    SELECT enrollment.id
                    FROM MemberClassEnrollment enrollment
                    WHERE enrollment.student.id = profile.userId
                      AND enrollment.schoolClass.id = :classId
                      AND enrollment.schoolClass.homeroomTeacher.id = :teacherId
                      AND enrollment.schoolClass.deletedAt IS NULL
              ))
            """)
    Page<MemberStudentProfile> findOwnedStudents(
            @Param("teacherId") Long teacherId,
            @Param("registrationYear") Short registrationYear,
            @Param("grade") Short grade,
            @Param("classId") Long classId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
