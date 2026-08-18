package com.cenedu.backend.domain.problem.authoring.asset;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.cenedu.backend.global.common.enums.QuestionType;

/** AI 생성 자산의 임시·최종 storage key를 기존 원천 경로와 분리해 만든다. */
public final class GeneratedAssetStorageKeyFactory {

    private static final String GENERATED_ROOT = "questions/generated";
    private static final Pattern ASSET_KEY = Pattern.compile("F[1-9][0-9]*");
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Map<QuestionType, String> TYPE_DIRECTORIES = Map.of(
            QuestionType.MULTIPLE_CHOICE, "multiple-choice",
            QuestionType.SHORT_INPUT, "short-input",
            QuestionType.STEP_FILL, "step-fill",
            QuestionType.ESSAY, "essay"
    );

    private GeneratedAssetStorageKeyFactory() {
    }

    /** 교사 승인 전에만 사용할 Session·Version 단위 임시 자산 key를 만든다. */
    public static String draftKey(AssetProductionContext context, String assetKey,
                                  AssetOutputFormat outputFormat) {
        requirePositive(context.sessionId(), "sessionId");
        requirePositive(context.versionNo(), "versionNo");
        return "%s/%s/drafts/%d/%d/%s.%s".formatted(
                GENERATED_ROOT,
                typeDirectory(context.questionType()),
                context.sessionId(),
                context.versionNo(),
                requireAssetKey(assetKey),
                extension(outputFormat));
    }

    /** 최종 승인 후 S3에 적재할 questionId·checksum 기반 불변 key를 만든다. */
    public static String finalKey(Long questionId, QuestionType questionType, String assetKey,
                                  String checksum, AssetOutputFormat outputFormat) {
        requirePositive(questionId, "questionId");
        return "%s/%s/%d/%s-%s.%s".formatted(
                GENERATED_ROOT,
                typeDirectory(questionType),
                questionId,
                requireAssetKey(assetKey),
                requireSafeToken(checksum, "checksum"),
                extension(outputFormat));
    }

    private static String typeDirectory(QuestionType questionType) {
        String directory = TYPE_DIRECTORIES.get(questionType);
        if (directory == null) {
            throw new IllegalArgumentException("지원하지 않는 문항 유형입니다: " + questionType);
        }
        return directory;
    }

    private static String extension(AssetOutputFormat outputFormat) {
        if (outputFormat == null) {
            throw new IllegalArgumentException("outputFormat은 필수입니다.");
        }
        return outputFormat.name().toLowerCase(Locale.ROOT);
    }

    private static String requireSafeToken(String value, String fieldName) {
        if (value == null || !SAFE_TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "은 영문·숫자·_·-만 사용할 수 있습니다.");
        }
        return value;
    }

    private static String requireAssetKey(String value) {
        if (value == null || !ASSET_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("assetKey는 S1과 동일하게 F1, F2 형식이어야 합니다.");
        }
        return value;
    }

    private static void requirePositive(Number value, String fieldName) {
        if (value == null || value.longValue() <= 0) {
            throw new IllegalArgumentException(fieldName + "은 1 이상이어야 합니다.");
        }
    }
}
