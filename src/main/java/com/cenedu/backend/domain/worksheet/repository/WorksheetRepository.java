package com.cenedu.backend.domain.worksheet.repository;

import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.domain.worksheet.repository.row.WorksheetListRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorksheetRepository extends JpaRepository<Worksheet, Long> {

    /** 삭제되지 않은 학습지를 ID와 소유 교사로 조회한다. */
    Optional<Worksheet> findByIdAndOwnerTeacherIdAndDeletedAtIsNull(Long id, Long ownerTeacherId);

    /**
     * 필터 조건을 적용한 교사 소유 학습지 목록을 최신순으로 조회한다.
     * sourceAssignment·그 학습지로 자기 조인해 맞춤 학습지의 출처 유형·출처 학습지 ID를 함께 얻는다.
     */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.WorksheetListRow(
                w.id, w.title, w.type, w.origin, w.grade, w.semester, w.totalScore, w.createdAt, sw.id)
            from Worksheet w
            left join w.sourceAssignment sa
            left join sa.worksheet sw
            where w.deletedAt is null and w.ownerTeacherId = :teacherId
              and (:grade is null or w.grade = :grade)
              and (:semester is null or w.semester = :semester)
              and (:originFilter is null or w.origin = :originFilter)
              and (:typeFilter is null or coalesce(sw.type, w.type) = :typeFilter)
            order by w.createdAt desc, w.id desc
            """)
    List<WorksheetListRow> findAllForList(
            @Param("teacherId") Long teacherId,
            @Param("grade") Short grade,
            @Param("semester") String semester,
            @Param("originFilter") WorksheetOrigin originFilter,
            @Param("typeFilter") WorksheetType typeFilter);

    /** 제목 검색어까지 적용한 교사 소유 학습지 목록을 최신순으로 조회한다. */
    @Query("""
            select new com.cenedu.backend.domain.worksheet.repository.row.WorksheetListRow(
                w.id, w.title, w.type, w.origin, w.grade, w.semester, w.totalScore, w.createdAt, sw.id)
            from Worksheet w
            left join w.sourceAssignment sa
            left join sa.worksheet sw
            where w.deletedAt is null and w.ownerTeacherId = :teacherId
              and (:grade is null or w.grade = :grade)
              and (:semester is null or w.semester = :semester)
              and (:originFilter is null or w.origin = :originFilter)
              and (:typeFilter is null or coalesce(sw.type, w.type) = :typeFilter)
              and lower(w.title) like lower(concat('%', :keyword, '%'))
            order by w.createdAt desc, w.id desc
            """)
    List<WorksheetListRow> findAllForListByKeyword(
            @Param("teacherId") Long teacherId,
            @Param("grade") Short grade,
            @Param("semester") String semester,
            @Param("originFilter") WorksheetOrigin originFilter,
            @Param("typeFilter") WorksheetType typeFilter,
            @Param("keyword") String keyword);
}
