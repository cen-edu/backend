package com.cenedu.backend.ai.problem.adapter;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.port.ProblemImageRevisionPort;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotValidationException;
import com.cenedu.backend.domain.problem.authoring.visual.ProblemImageRevisionCommand;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationMode;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.domain.problem.authoring.visual.VisualSnapshotConsistencyValidator;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;

/** 이미지 필요 신호를 검증하고 종류별 생성 전략을 제한 횟수 안에서 실행한다. */
@Component
public final class ProblemImageGenerationLoop implements ProblemImageRevisionPort {

    private static final int MAX_IMAGE_GENERATION_ATTEMPTS = 2;

    private final Map<VisualReferenceKind, ProblemImageGenerator> generators;
    private final VisualSnapshotConsistencyValidator visualConsistencyValidator;

    public ProblemImageGenerationLoop(List<ProblemImageGenerator> generators,
                                      VisualSnapshotConsistencyValidator visualConsistencyValidator) {
        this.generators = register(generators == null ? List.of() : generators);
        this.visualConsistencyValidator = visualConsistencyValidator;
    }

    /** 필요하면 이미지를 생성해 붙이고, 이미지가 없더라도 최종 시각 사용 가능성을 검증한다. */
    public ProblemCandidateDraft generate(ProblemCandidateDraft candidate,
                                          ProblemGenerationOutput output,
                                          ProblemGenerationCommand command) {
        ImageRequirement requirement = resolve(output, command);
        if (!requirement.required()) {
            visualConsistencyValidator.validateFinal(candidate.snapshot());
            return candidate;
        }

        ProblemImageGenerator generator = generators.get(requirement.kind());
        if (generator == null) {
            throw violations("imageGeneration.visualKind: 지원하지 않는 이미지 종류입니다: "
                    + requirement.kind());
        }

        RuntimeException previousFailure = null;
        for (int attempt = 0; attempt < MAX_IMAGE_GENERATION_ATTEMPTS; attempt++) {
            ProblemCandidateDraft generated;
            try {
                generated = generator.generate(candidate,
                        requirement.description(), command, attempt, previousFailure);
            } catch (RuntimeException exception) {
                previousFailure = exception;
                continue;
            }
            // 본문과 이미지의 최종 불일치는 이미지를 반복 생성해도 고쳐지지 않으므로, 이미지
            // 루프 밖으로 즉시 전달해 상위 문제 후보 교정 루프가 현재 후보를 수정하게 한다.
            visualConsistencyValidator.validateFinal(generated.snapshot());
            return generated;
        }
        throw new IllegalStateException("이미지 생성에 실패했습니다: "
                + (previousFailure == null ? "unknown" : previousFailure.getMessage()),
                previousFailure);
    }

    /** 자산 검증 피드백을 반영해 기존 시각 자산만 제거한 뒤 같은 종류의 이미지를 다시 만든다. */
    @Override
    public ProblemCandidateDraft revise(ProblemImageRevisionCommand command) {
        if (command == null || command.candidate() == null || command.generationCommand() == null) {
            throw new IllegalArgumentException("이미지 재생성 필수값이 누락되었습니다.");
        }
        ProblemCandidateDraft candidate = command.candidate();
        var plan = candidate.assetPlans().stream()
                .filter(value -> value.specification() != null
                        && value.specification().diagramSpec() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("재생성할 구조화 이미지 계획이 없습니다."));
        VisualReferenceKind kind = visualKind(plan.specification().diagramSpec().kind());
        ProblemImageGenerator generator = generators.get(kind);
        if (generator == null) {
            throw new IllegalArgumentException("재생성을 지원하지 않는 이미지 종류입니다: " + kind);
        }

        ProblemCandidateDraft base = removeCurrentImage(candidate);
        RuntimeException feedback = new IllegalStateException(feedbackMessage(command));
        ProblemCandidateDraft revised = generator.generate(base,
                plan.specification().visualDescription(), command.generationCommand(),
                Math.max(1, command.attempt()), feedback);
        visualConsistencyValidator.validateFinal(revised.snapshot());
        UUID revisedRequestId = UUID.nameUUIDFromBytes((candidate.requestId() + ":image-revision:"
                + command.attempt()).getBytes(StandardCharsets.UTF_8));
        return ProblemCandidateDraft.legacy(revisedRequestId, revised.snapshot(),
                revised.assetPlans(), revised.provenance());
    }

    private ProblemCandidateDraft removeCurrentImage(ProblemCandidateDraft candidate) {
        Set<String> imageKeys = candidate.assetPlans().stream()
                .filter(plan -> plan.specification() != null && plan.specification().diagramSpec() != null)
                .map(plan -> plan.assetKey())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        QuestionSnapshotV1 snapshot = candidate.snapshot();
        var blocks = snapshot.contentBlocks().stream()
                .filter(block -> block.assetRef() == null || !imageKeys.contains(block.assetRef()))
                .toList();
        var assets = snapshot.assets().stream()
                .filter(asset -> !imageKeys.contains(asset.assetKey()))
                .toList();
        var plans = candidate.assetPlans().stream()
                .filter(plan -> !imageKeys.contains(plan.assetKey()))
                .toList();
        QuestionPresentation presentation = blocks.stream()
                .anyMatch(block -> block.blockKind()
                        == com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind.FIGURE)
                ? QuestionPresentation.WITH_FIGURE
                : blocks.stream().anyMatch(block -> block.blockKind()
                        == com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind.TABLE)
                ? QuestionPresentation.WITH_TABLE : QuestionPresentation.TEXT_ONLY;
        SnapshotMetadata metadata = new SnapshotMetadata(snapshot.metadata().questionType(), presentation,
                snapshot.metadata().difficulty(), snapshot.metadata().subUnitId(), snapshot.metadata().topicCode(),
                snapshot.metadata().evaluationArea(), snapshot.metadata().derivedFromQuestionId());
        QuestionSnapshotV1 withoutImage = new QuestionSnapshotV1(snapshot.schemaVersion(), metadata, blocks, assets,
                snapshot.choices(), snapshot.steps(), snapshot.answerUnits(), snapshot.explanation(),
                snapshot.learningGuide(), snapshot.rubricItems());
        return ProblemCandidateDraft.legacy(candidate.requestId(), withoutImage, plans, candidate.provenance());
    }

    private VisualReferenceKind visualKind(DiagramKind kind) {
        return switch (kind) {
            case COORDINATE_GRAPH -> VisualReferenceKind.COORDINATE_GRAPH;
            default -> throw new IllegalArgumentException("재생성 전략이 없는 도식 종류입니다: " + kind);
        };
    }

    private String feedbackMessage(ProblemImageRevisionCommand command) {
        String joined = command.findings().stream()
                .filter(finding -> finding.message() != null || finding.evidence() != null)
                .map(finding -> (finding.message() == null ? "" : finding.message()) + " "
                        + (finding.evidence() == null ? "" : finding.evidence()))
                .collect(java.util.stream.Collectors.joining("; "));
        if (joined.isBlank()) return "자산 검증을 통과하지 못했습니다.";
        return joined.length() > 500 ? joined.substring(0, 500) : joined;
    }

    private ImageRequirement resolve(ProblemGenerationOutput output,
                                     ProblemGenerationCommand command) {
        var requested = command.specification().visualRequirement();
        boolean forced = requested.mode() == VisualGenerationMode.REQUIRED;

        if (!forced && !output.visualRequired()) {
            if (output.visualKind() != null || output.visualDescription() != null) {
                throw violations("imageGeneration: visualRequired=false이면 visualKind와 visualDescription은 null이어야 합니다.");
            }
            return new ImageRequirement(false, VisualReferenceKind.NONE, null);
        }
        if (!output.visualRequired()) {
            throw violations("imageGeneration: 필수 이미지 문항은 visualRequired=true여야 합니다.");
        }
        if (!hasText(output.visualKind())) {
            throw violations("imageGeneration.visualKind: 이미지가 필요하면 종류가 필수입니다.");
        }
        if (!hasText(output.visualDescription())) {
            throw violations("imageGeneration.visualDescription: 이미지가 필요하면 구체적인 설명이 필수입니다.");
        }

        VisualReferenceKind outputKind;
        try {
            outputKind = VisualReferenceKind.valueOf(output.visualKind());
        } catch (IllegalArgumentException exception) {
            throw violations("imageGeneration.visualKind: 알 수 없는 이미지 종류입니다: " + output.visualKind());
        }
        VisualReferenceKind kind = forced ? requested.requiredKind() : outputKind;
        if (forced && kind != outputKind) {
            throw violations("imageGeneration.visualKind: 요청한 이미지 종류와 생성 결과가 다릅니다. expected="
                    + kind + ", actual=" + outputKind);
        }
        return new ImageRequirement(true, kind, output.visualDescription());
    }

    private Map<VisualReferenceKind, ProblemImageGenerator> register(
            List<ProblemImageGenerator> values) {
        Map<VisualReferenceKind, ProblemImageGenerator> result =
                new EnumMap<>(VisualReferenceKind.class);
        for (ProblemImageGenerator generator : values) {
            if (generator == null || generator.kind() == null) {
                throw new IllegalArgumentException("이미지 생성기와 지원 종류는 필수입니다.");
            }
            if (result.putIfAbsent(generator.kind(), generator) != null) {
                throw new IllegalStateException("같은 이미지 종류의 생성기가 중복되었습니다: "
                        + generator.kind());
            }
        }
        return Map.copyOf(result);
    }

    private SnapshotValidationException violations(String message) {
        return new SnapshotValidationException(List.of(message));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ImageRequirement(boolean required, VisualReferenceKind kind,
                                    String description) {
    }
}
