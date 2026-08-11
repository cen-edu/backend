package com.cenedu.backend.domain.member.repository;

import java.util.ArrayList;
import java.util.List;

import com.cenedu.backend.domain.member.entity.MemberClassEnrollment;
import com.cenedu.backend.domain.member.entity.MemberStudentProfile;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/** 학생 목록의 선택 조회 조건을 조합한다. */
public final class MemberStudentProfileSpecifications {

    private static final char LIKE_ESCAPE_CHARACTER = '\\';

    private MemberStudentProfileSpecifications() {
    }

    /** 교사 소유 학생에 전달된 등록연도, 학년, 반, 이름 조건만 적용한다. */
    public static Specification<MemberStudentProfile> ownedStudents(
            long teacherId,
            Short registrationYear,
            Short grade,
            Long classId,
            String keyword
    ) {
        return (profile, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(
                    profile.get("ownerTeacher").get("id"), teacherId));
            predicates.add(criteriaBuilder.isNull(profile.get("user").get("deletedAt")));

            if (registrationYear != null) {
                predicates.add(criteriaBuilder.equal(
                        profile.get("registrationYear"), registrationYear));
            }
            if (grade != null) {
                predicates.add(criteriaBuilder.equal(profile.get("grade"), grade));
            }
            if (keyword != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(profile.get("user").<String>get("name")),
                        "%" + escapeLike(keyword) + "%",
                        LIKE_ESCAPE_CHARACTER));
            }
            if (classId != null) {
                predicates.add(criteriaBuilder.exists(activeClassEnrollment(
                        profile,
                        query.subquery(Long.class),
                        criteriaBuilder,
                        teacherId,
                        classId)));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** 학생이 교사 소유의 활성 반에 배정됐는지 검사하는 서브쿼리를 반환한다. */
    private static Subquery<Long> activeClassEnrollment(
            Root<MemberStudentProfile> profile,
            Subquery<Long> subquery,
            CriteriaBuilder criteriaBuilder,
            long teacherId,
            long classId
    ) {
        Root<MemberClassEnrollment> enrollment = subquery.from(MemberClassEnrollment.class);
        return subquery.select(enrollment.get("id"))
                .where(
                        criteriaBuilder.equal(
                                enrollment.get("student").get("id"), profile.get("userId")),
                        criteriaBuilder.equal(
                                enrollment.get("schoolClass").get("id"), classId),
                        criteriaBuilder.equal(
                                enrollment.get("schoolClass").get("homeroomTeacher").get("id"),
                                teacherId),
                        criteriaBuilder.isNull(
                                enrollment.get("schoolClass").get("deletedAt"))
                );
    }

    /** LIKE 검색에서 특별한 의미를 갖는 문자를 일반 문자로 이스케이프한다. */
    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
