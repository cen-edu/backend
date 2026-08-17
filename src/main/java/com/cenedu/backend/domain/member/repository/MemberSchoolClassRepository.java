package com.cenedu.backend.domain.member.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.member.entity.MemberSchoolClass;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 학년도 단위 반 영속성 저장소. */
public interface MemberSchoolClassRepository extends JpaRepository<MemberSchoolClass, Long> {

    /** 교사가 소유한 삭제되지 않은 반을 표시 순서대로 조회한다. */
    List<MemberSchoolClass> findAllByHomeroomTeacherIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            Long teacherId);

    /** 교사 소유 활성 반을 선택된 학년도와 학년 조건으로 조회한다. */
    @EntityGraph(attributePaths = "homeroomTeacher")
    @Query("""
            SELECT schoolClass
            FROM MemberSchoolClass schoolClass
            WHERE schoolClass.homeroomTeacher.id = :teacherId
              AND schoolClass.deletedAt IS NULL
              AND (:academicYear IS NULL OR schoolClass.academicYear = :academicYear)
              AND (:grade IS NULL OR schoolClass.grade = :grade)
            ORDER BY schoolClass.displayOrder ASC, schoolClass.id ASC
            """)
    List<MemberSchoolClass> findAllForClassList(
            @Param("teacherId") Long teacherId,
            @Param("academicYear") Short academicYear,
            @Param("grade") Short grade);

    /** 교사 소유 활성 반을 선택된 학년도, 학년, 반 이름 검색어 조건으로 조회한다. */
    @EntityGraph(attributePaths = "homeroomTeacher")
    @Query("""
            SELECT schoolClass
            FROM MemberSchoolClass schoolClass
            WHERE schoolClass.homeroomTeacher.id = :teacherId
              AND schoolClass.deletedAt IS NULL
              AND (:academicYear IS NULL OR schoolClass.academicYear = :academicYear)
              AND (:grade IS NULL OR schoolClass.grade = :grade)
              AND LOCATE(LOWER(:keyword), LOWER(schoolClass.name)) > 0
            ORDER BY schoolClass.displayOrder ASC, schoolClass.id ASC
            """)
    List<MemberSchoolClass> findAllForClassListByKeyword(
            @Param("teacherId") Long teacherId,
            @Param("academicYear") Short academicYear,
            @Param("grade") Short grade,
            @Param("keyword") String keyword);

    /** 반 ID로 삭제되지 않은 반을 조회한다. */
    Optional<MemberSchoolClass> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 지정한 ID 중 교사가 소유한 활성 반을 한 번에 조회한다.
     *
     * <p>소유와 소프트 삭제를 쿼리에서 거른다 — {@code homeroomTeacher}가 LAZY 라 Java 쪽에서
     * 거르면 반마다 지연 로딩이 돈다.
     */
    List<MemberSchoolClass> findAllByIdInAndHomeroomTeacherIdAndDeletedAtIsNull(
            Collection<Long> ids, Long teacherId);

    /** 교사가 소유한 마지막 표시 순서의 반을 조회한다. */
    Optional<MemberSchoolClass> findTopByHomeroomTeacherIdAndDeletedAtIsNullOrderByDisplayOrderDesc(
            Long teacherId);
}
