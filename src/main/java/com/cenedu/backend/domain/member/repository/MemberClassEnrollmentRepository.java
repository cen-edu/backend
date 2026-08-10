package com.cenedu.backend.domain.member.repository;

import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.member.entity.MemberClassEnrollment;

import org.springframework.data.jpa.repository.JpaRepository;

/** 반과 학생 배정 영속성 저장소. */
public interface MemberClassEnrollmentRepository
        extends JpaRepository<MemberClassEnrollment, Long> {

    /** 한 반에 같은 학생이 이미 배정되어 있는지 확인한다. */
    boolean existsBySchoolClassIdAndStudentId(Long classId, Long studentId);

    /** 반에 배정된 학생을 이름과 계정 ID 순으로 조회한다. */
    List<MemberClassEnrollment> findAllBySchoolClassIdOrderByStudentNameAscStudentIdAsc(
            Long classId);

    /** 반과 학생 ID로 배정 정보를 조회한다. */
    Optional<MemberClassEnrollment> findBySchoolClassIdAndStudentId(Long classId, Long studentId);

    /** 반에 배정된 학생 수를 반환한다. */
    long countBySchoolClassId(Long classId);
}
