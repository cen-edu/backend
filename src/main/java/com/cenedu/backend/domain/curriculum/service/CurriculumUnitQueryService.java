package com.cenedu.backend.domain.curriculum.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.curriculum.dto.response.CurriculumUnitResponse;
import com.cenedu.backend.domain.curriculum.entity.CurriculumUnit;
import com.cenedu.backend.domain.curriculum.repository.CurriculumUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumUnitQueryService {

    private final CurriculumUnitRepository curriculumUnitRepository;

    public List<CurriculumUnitResponse> getUnits(
        short grade,
        short semester
    ) {
        List<CurriculumUnit> units =
            curriculumUnitRepository
                .findAllByGradeAndSemesterOrderByDisplayOrder(
                    grade,
                    semester
                );

        Map<Long, List<CurriculumUnit>> childrenByParentId =
            new HashMap<>();

        List<CurriculumUnit> roots = new ArrayList<>();

        for (CurriculumUnit unit : units) {
            CurriculumUnit parent = unit.getParent();

            if (parent == null) {
                roots.add(unit);
                continue;
            }

            childrenByParentId
                .computeIfAbsent(parent.getId(), key -> new ArrayList<>())
                .add(unit);
        }

        return roots.stream()
            .map(root -> toResponse(root, childrenByParentId))
            .toList();
    }

    private CurriculumUnitResponse toResponse(
        CurriculumUnit unit,
        Map<Long, List<CurriculumUnit>> childrenByParentId
    ) {
        List<CurriculumUnitResponse> children =
            childrenByParentId
                .getOrDefault(unit.getId(), List.of())
                .stream()
                .map(child -> toResponse(child, childrenByParentId))
                .toList();

        return new CurriculumUnitResponse(
            unit.getId(),
            unit.getName(),
            unit.getUnitLevel(),
            unit.getDisplayOrder(),
            children
        );
    }
}
