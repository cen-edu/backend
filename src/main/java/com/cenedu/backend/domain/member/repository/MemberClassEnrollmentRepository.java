package com.cenedu.backend.domain.member.repository;

import java.util.List;

import com.cenedu.backend.domain.member.entity.MemberClassEnrollment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 반과 학생 배정 영속성 저장소. */
public interface MemberClassEnrollmentRepository
        extends JpaRepository<MemberClassEnrollment, Long> {

    /** 반에 배정된 활성 학생을 이름과 계정 ID 순으로 조회한다. */
    List<MemberClassEnrollment>
    findAllBySchoolClassIdAndStudentDeletedAtIsNullOrderByStudentNameAscStudentIdAsc(
            Long classId);

    /** 반의 전체 학생 배정을 학생 계정과 함께 조회한다. */
    @EntityGraph(attributePaths = "student")
    List<MemberClassEnrollment> findAllBySchoolClassId(Long classId);

    /** 반에 배정된 활성 학생 수를 반환한다. */
    long countBySchoolClassIdAndStudentDeletedAtIsNull(Long classId);

    /** 교사가 소유한 활성 반에 속한 지정 학생들의 배정 정보를 조회한다. */
    @EntityGraph(attributePaths = {"student", "schoolClass"})
    @Query("""
            SELECT enrollment
            FROM MemberClassEnrollment enrollment
            WHERE enrollment.schoolClass.homeroomTeacher.id = :teacherId
              AND enrollment.schoolClass.deletedAt IS NULL
              AND enrollment.student.id IN :studentIds
            ORDER BY enrollment.schoolClass.academicYear DESC,
                     enrollment.schoolClass.displayOrder ASC,
                     enrollment.schoolClass.id ASC
            """)
    List<MemberClassEnrollment> findAllActiveByTeacherIdAndStudentIdIn(
            @Param("teacherId") Long teacherId,
            @Param("studentIds") List<Long> studentIds);
}
