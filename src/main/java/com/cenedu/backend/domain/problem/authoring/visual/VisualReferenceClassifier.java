package com.cenedu.backend.domain.problem.authoring.visual;

import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import java.util.regex.Pattern;

/** 구조화 정보가 없는 레거시 시각 자산을 강한 텍스트 근거로만 보수적으로 분류한다. */
public final class VisualReferenceClassifier {
    private static final Pattern COORDINATE_AXES = Pattern.compile(
            "좌표\\s*평면|x\\s*축.{0,80}y\\s*축|y\\s*축.{0,80}x\\s*축",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PROPORTIONAL_GRAPH = Pattern.compile(
            "(?:정비례|반비례).{0,80}그래프|그래프.{0,80}(?:정비례|반비례)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 표현 방식·자산 역할·문제와 대체 텍스트를 근거로 안전한 시각 유형을 반환한다. */
    public VisualReferenceKind classify(QuestionPresentation presentation, AssetRole role,
            String problemText, String altText) {
        if (presentation == QuestionPresentation.TEXT_ONLY) return VisualReferenceKind.NONE;
        if (presentation == QuestionPresentation.WITH_TABLE || role == AssetRole.TABLE) {
            return VisualReferenceKind.DATA_TABLE;
        }
        String evidence = text(problemText) + "\n" + text(altText);
        if (COORDINATE_AXES.matcher(evidence).find()
                || PROPORTIONAL_GRAPH.matcher(evidence).find()) {
            return VisualReferenceKind.COORDINATE_GRAPH;
        }
        return VisualReferenceKind.UNKNOWN_FIGURE;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
