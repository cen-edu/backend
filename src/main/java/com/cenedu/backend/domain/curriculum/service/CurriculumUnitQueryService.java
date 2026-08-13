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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.curriculum.entity.enums.UnitLevel;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumUnitQueryService {

    private final CurriculumUnitRepository curriculumUnitRepository;

    // 학년과 학기에 속한 단원 트리를 조회한다.
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

    // 여러 소단원의 대단원·중단원·소단원 경로를 소단원 ID 기준으로 반환한다.
    public Map<Long, CurriculumPathResponse> getPathsBySubUnitIds(
        Collection<Long> subUnitIds
    ) {
        Set<Long> uniqueSubUnitIds = new HashSet<>(subUnitIds);

        if (uniqueSubUnitIds.isEmpty()) {
            return Map.of();
        }

        List<CurriculumUnit> subUnits =
            curriculumUnitRepository.findAllWithParentPathByIds(
                uniqueSubUnitIds
            );

        if (subUnits.size() != uniqueSubUnitIds.size()) {
            throw new BusinessException(
                ErrorCode.CURRICULUM_SUB_UNIT_NOT_FOUND
            );
        }

        Map<Long, CurriculumPathResponse> pathsBySubUnitId =
            new HashMap<>();

        for (CurriculumUnit subUnit : subUnits) {
            CurriculumUnit middleUnit = subUnit.getParent();

            if (subUnit.getUnitLevel() != UnitLevel.SUB_UNIT
                || middleUnit == null
                || middleUnit.getUnitLevel() != UnitLevel.MIDDLE_UNIT) {
                throw new BusinessException(
                    ErrorCode.CURRICULUM_SUB_UNIT_NOT_FOUND
                );
            }

            CurriculumUnit majorUnit = middleUnit.getParent();

            if (majorUnit == null
                || majorUnit.getUnitLevel() != UnitLevel.MAJOR_UNIT) {
                throw new BusinessException(
                    ErrorCode.CURRICULUM_SUB_UNIT_NOT_FOUND
                );
            }

            pathsBySubUnitId.put(
                subUnit.getId(),
                CurriculumPathResponse.from(
                    majorUnit,
                    middleUnit,
                    subUnit
                )
            );
        }

        return Map.copyOf(pathsBySubUnitId);
    }
    // 현재 단원과 하위 단원을 재귀적으로 응답 트리로 변환한다.
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
