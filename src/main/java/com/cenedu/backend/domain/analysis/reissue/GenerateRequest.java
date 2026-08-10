package com.cenedu.backend.domain.analysis.reissue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

/** 교사가 표에서 숫자를 조정한 뒤 보내는 값. */
@Schema(description = "조정된 문항 구성")
public record GenerateRequest(

        @NotEmpty(message = "문항 구성이 비어 있습니다.")
        @Valid
        List<ConfigRequest> configs
) {

    List<ReissueProposalService.Request> toRequests() {
        List<ReissueProposalService.Request> requests = new ArrayList<>();
        for (ConfigRequest config : configs) {
            requests.add(new ReissueProposalService.Request(
                    config.conceptId(), config.toCounts()));
        }
        return requests;
    }

    @Schema(description = "개념 하나의 칸별 문항 수")
    public record ConfigRequest(
            @NotBlank(message = "conceptId 는 필수입니다.")
            String conceptId,

            @Schema(description = "키는 retrace / basic / independent. 없는 키는 0으로 본다",
                    example = "{\"retrace\": 0, \"basic\": 3, \"independent\": 0}")
            Map<String, Integer> counts
    ) {
        Map<ReissueStage, Integer> toCounts() {
            Map<ReissueStage, Integer> out = new EnumMap<>(ReissueStage.class);
            for (ReissueStage stage : ReissueStage.values()) {
                Integer value = counts == null ? null : counts.get(stage.code());
                // 화면이 개념당 칸마다 최대 5문항으로 막고 있지만, 서버도 같은 상한을 둔다.
                // 클라이언트만 막으면 조작된 요청에 뱅크 재고가 통째로 나간다.
                out.put(stage, value == null ? 0 : Math.clamp(value, 0, 5));
            }
            return out;
        }
    }
}
