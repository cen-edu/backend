package com.cenedu.backend.domain.curriculum.repository;

import com.cenedu.backend.domain.curriculum.entity.CurriculumUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurriculumUnitRepository extends JpaRepository<CurriculumUnit, Long> {
    List<CurriculumUnit> findAllByGradeAndSemesterOrderByDisplayOrder(
        short grade,
        short semester
    );
}
