package com.cenedu.backend.domain.member.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 반 관리 목록에서 선택한 반을 일괄 삭제하는 요청. */
public record SchoolClassDeleteRequest(
        @NotEmpty(message = "삭제할 반을 하나 이상 선택해야 합니다.")
        List<@NotNull(message = "반 ID는 필수입니다.")
                @Positive(message = "반 ID는 양수여야 합니다.") Long> classIds
) {
}
