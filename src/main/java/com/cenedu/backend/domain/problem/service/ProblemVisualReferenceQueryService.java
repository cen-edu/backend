package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.visual.*;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.repository.*;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.ProblemSemanticDocumentCodec;
import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;
import tools.jackson.databind.ObjectMapper;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.stereotype.Service;

/** 저장된 문항의 표현과 자산 role을 자동 생성용 visual kind 정본으로 변환한다. */
@Service
public class ProblemVisualReferenceQueryService {
    private final ProblemQuestionRepository questions;
    private final ProblemAssetRepository assets;
    private final ProblemSemanticDocumentCodec codec;

    public ProblemVisualReferenceQueryService(ProblemQuestionRepository questions, ProblemAssetRepository assets,
                                              ObjectMapper objectMapper) {
        this.questions = questions; this.assets = assets; this.codec = new ProblemSemanticDocumentCodec(objectMapper);
    }

    /** 문제의 시각 유형을 충돌 시 UNKNOWN으로 보수적으로 판정한다. */
    public VisualReferenceDescriptor get(long questionId) {
        var question = questions.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_DETAIL_DATA_INVALID));
        var values = assets.findAllByQuestionIdOrderByDisplayOrderAscIdAsc(questionId);
        if (question.getPresentation() == QuestionPresentation.TEXT_ONLY || values.isEmpty())
            return new VisualReferenceDescriptor(null, VisualReferenceKind.NONE, null, "", null);
        if (values.size() != 1) return unknown(values.get(0));
        var asset = values.get(0);
        var semantic = semanticDiagram(question.getSemanticModel());
        if (semantic != null) {
            VisualReferenceKind semanticKind = VisualReferenceKind.valueOf(semantic.kind().name());
            VisualReferenceKind assetKind = kindFromRenderSpec(asset.getRenderSpec());
            if (assetKind != VisualReferenceKind.UNKNOWN_FIGURE && assetKind != semanticKind)
                return unknown(asset);
            return new VisualReferenceDescriptor(asset.getAssetKey(), semanticKind, asset.getRole(), asset.getAltText(), semantic);
        }
        if (asset.getRole() == AssetRole.TABLE || question.getPresentation() == QuestionPresentation.WITH_TABLE)
            return new VisualReferenceDescriptor(asset.getAssetKey(), VisualReferenceKind.DATA_TABLE, asset.getRole(), asset.getAltText(), null);
        VisualReferenceKind kind = kindFromRenderSpec(asset.getRenderSpec());
        return new VisualReferenceDescriptor(asset.getAssetKey(), kind, asset.getRole(), asset.getAltText(), null);
    }

    private com.cenedu.backend.domain.problem.authoring.diagram.DiagramSpecV1 semanticDiagram(String json) {
        if (json == null) return null;
        try {
            var diagrams = codec.readSemanticModel(json).diagrams();
            return diagrams.size() == 1 ? diagrams.getFirst() : null;
        } catch (RuntimeException ignored) { return null; }
    }

    private VisualReferenceKind kindFromRenderSpec(String renderSpec) {
        if (renderSpec == null) return VisualReferenceKind.UNKNOWN_FIGURE;
        try {
            DiagramKind kind = codec.readRenderSpec(renderSpec).kind();
            return VisualReferenceKind.valueOf(kind.name());
        } catch (RuntimeException ignored) { }
        return VisualReferenceKind.UNKNOWN_FIGURE;
    }

    private VisualReferenceDescriptor unknown(com.cenedu.backend.domain.problem.entity.ProblemAsset asset) {
        return new VisualReferenceDescriptor(asset.getAssetKey(), VisualReferenceKind.UNKNOWN_FIGURE,
                asset.getRole(), asset.getAltText(), null);
    }
}
