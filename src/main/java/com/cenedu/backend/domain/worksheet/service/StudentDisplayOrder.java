package com.cenedu.backend.domain.worksheet.service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 학생 표시 순번의 정렬 규칙. 학습 현황의 여러 표가 <b>같은 학생에게 같은 번호</b>를 주도록
 * 규칙을 한 곳에 둔다 — 표마다 복사하면 같은 화면에서 같은 번호가 다른 학생을 가리킨다.
 *
 * <p>이름 정렬은 {@link Collator}(한국어)를 쓴다. {@code String.compareTo}는 유니코드 코드포인트
 * 순이라 한글 이름 정렬이 직관과 어긋난다. 이름이 같으면 {@code studentId} 오름차순으로 안정
 * 정렬하고, 이름을 못 찾은 학생은 뒤로 보낸다.
 */
public final class StudentDisplayOrder {

    private StudentDisplayOrder() {
    }

    /** 학생 ID를 이름 기준으로 세운 순서. */
    public static List<Long> order(Collection<Long> studentIds, Map<Long, String> namesByStudentId) {
        Collator collator = Collator.getInstance(Locale.KOREAN);
        return studentIds.stream()
                .distinct()
                .sorted(Comparator
                        .comparing((Long studentId) -> namesByStudentId.get(studentId) == null)
                        .thenComparing(studentId -> namesByStudentId.getOrDefault(studentId, ""), collator)
                        .thenComparing(studentId -> studentId))
                .toList();
    }

    /**
     * 명단 기준 1-based 번호.
     *
     * <p>{@code extraStudentIds}는 명단에 없는 학생이다(배포 후 명단이 바뀐 경우). 예외를 던지지
     * 않고 명단 뒤에 같은 규칙으로 이어 붙인다 — 학생 한 명 때문에 화면 전체를 막지 않는다.
     */
    public static Map<Long, Integer> numbers(Collection<Long> rosterStudentIds,
                                      Collection<Long> extraStudentIds,
                                      Map<Long, String> namesByStudentId) {
        List<Long> ordered = new ArrayList<>(order(rosterStudentIds, namesByStudentId));
        Set<Long> known = new HashSet<>(ordered);
        ordered.addAll(order(
                extraStudentIds.stream().filter(studentId -> !known.contains(studentId)).toList(),
                namesByStudentId));

        Map<Long, Integer> numbers = new HashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            numbers.put(ordered.get(index), index + 1);
        }
        return numbers;
    }
}
