package com.cenedu.backend.domain.problem.authoring.port;

import com.cenedu.backend.domain.problem.authoring.asset.AssetProductionContext;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetArtifact;
import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;

/**
 * 문제 조율측이 임시 자산의 실제 렌더러·이미지 생성기·재사용 구현을 교체하는 경계다.
 *
 * <p>이하영이 계약과 승인 전 임시 자산 구현을 담당한다. 최종 승인 뒤 S3에 저장하는 연결은
 * 저장소 담당자와 협의하되, Adapter가 Session·Version 상태를 직접 변경하지 않는다.
 */
public interface ProblemAssetProductionPort {

    /** 자산 계획 하나를 승인 전 임시 자산 결과로 만든다. */
    DraftAssetArtifact produce(GeneratedAssetPlan plan, AssetProductionContext context);
}
