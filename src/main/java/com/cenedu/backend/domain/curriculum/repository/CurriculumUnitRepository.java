package com.cenedu.backend.domain.curriculum.repository;

import java.util.Collection;
import java.util.List;

import com.cenedu.backend.domain.curriculum.entity.CurriculumUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurriculumUnitRepository
    extends JpaRepository<CurriculumUnit, Long> {

    // 학년과 학기에 속한 전체 단원을 표시 순서대로 조회한다.
    List<CurriculumUnit> findAllByGradeAndSemesterOrderByDisplayOrder(
        short grade,
        short semester
    );

    // 여러 소단원과 각 소단원의 중단원·대단원을 한 번에 조회한다.
    @Query("""
        select subUnit
        from CurriculumUnit subUnit
        join fetch subUnit.parent middleUnit
        join fetch middleUnit.parent majorUnit
        where subUnit.id in :subUnitIds
        """)
    List<CurriculumUnit> findAllWithParentPathByIds(
        @Param("subUnitIds") Collection<Long> subUnitIds
    );
}
