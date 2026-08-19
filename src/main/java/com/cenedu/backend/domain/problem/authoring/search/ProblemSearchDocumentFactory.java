package com.cenedu.backend.domain.problem.authoring.search;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotLearningGuide;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProblemSearchDocumentFactory {
    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[\\t\\x0B\\f\\r ]+");
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(?:[.,]\\d+)?");

    /** 인덱싱 Snapshot에서 답안 없는 정규 검색 문서와 해시를 만든다. */
    public ProblemSearchDocument create(SearchIndexingCommand command) {
        QuestionSnapshotV1 snapshot = command.snapshot();
        String strategy = strategy(snapshot.learningGuide());
        String summary = summary(snapshot.learningGuide());
        String prompt = visiblePrompt(snapshot);
        String presentation = presentation(snapshot);
        String text = document(command.curriculum(), snapshot, prompt, strategy, summary, presentation);
        String normalizedPrompt = normalize(prompt);
        String duplicate = sha256(normalizedPrompt.replaceAll(NUMBER.pattern(), "#") + "|"
                + command.curriculum().subUnitId() + "|" + snapshot.metadata().questionType());
        return new ProblemSearchDocument(text, sha256(text), duplicate,
                sourceFamily(command.sourceRef(), command.questionId()), strategy, summary);
    }

    /** 검색 요구에서 같은 레이블 순서의 답안 없는 query 문서를 만든다. */
    public String createQuery(ProblemReferenceQuery query) {
        QuestionSnapshotV1 snapshot = query.originSnapshot();
        String prompt = snapshot == null ? "동일 교육과정 범위의 새 문제" : visiblePrompt(snapshot);
        String strategy = snapshot == null ? "동일 성취기준의 핵심 풀이 전략" : strategy(snapshot.learningGuide());
        String summary = snapshot == null ? "동일 난이도의 풀이 구조" : summary(snapshot.learningGuide());
        String presentation = snapshot == null ? "text-only" : presentation(snapshot);
        return document(query.curriculum(), snapshot, prompt, strategy, summary, presentation,
                query.questionType().name(), query.difficulty());
    }

    private static String document(CurriculumScope curriculum, QuestionSnapshotV1 snapshot,
                                   String prompt, String strategy, String summary, String presentation) {
        return document(curriculum, snapshot, prompt, strategy, summary, presentation,
                snapshot.metadata().questionType().name(), snapshot.metadata().difficulty());
    }

    private static String document(CurriculumScope curriculum, QuestionSnapshotV1 snapshot,
                                   String prompt, String strategy, String summary, String presentation,
                                   String questionType, String difficulty) {
        String achievement = curriculum.achievementStandardId() == null
                ? "MISSING:SUB_UNIT:" + curriculum.subUnitId() : curriculum.achievementStandardId();
        return normalize("[교육과정] 중학교 1학년 > " + curriculum.majorUnitName() + " > "
                + curriculum.middleUnitName() + " > " + curriculum.subUnitName() + "\n"
                + "[성취기준] " + achievement + "\n"
                + "[유형] " + questionType + "\n"
                + "[난이도] " + difficulty + "\n"
                + "[발문] " + prompt + "\n"
                + "[풀이전략] " + strategy + "\n"
                + "[풀이요약] " + summary + "\n"
                + "[표현] " + presentation);
    }

    private static String visiblePrompt(QuestionSnapshotV1 snapshot) {
        String prompt = snapshot.contentBlocks().stream()
                .sorted(Comparator.comparingInt(SnapshotContentBlock::displayOrder))
                .map(SnapshotContentBlock::text).filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + " " + right).orElse("");
        return normalize(prompt);
    }

    private static String strategy(SnapshotLearningGuide guide) {
        if (guide == null) return "개념 풀이 전략 없음";
        if (guide.keyPoints() != null && !guide.keyPoints().isEmpty()) {
            return String.join(" | ", guide.keyPoints());
        }
        return guide.conceptTitle() == null || guide.conceptTitle().isBlank()
                ? "개념 풀이 전략 없음" : guide.conceptTitle();
    }

    private static String summary(SnapshotLearningGuide guide) {
        return guide == null || guide.summary() == null || guide.summary().isBlank()
                ? "구조 요약 없음" : guide.summary();
    }

    private static String presentation(QuestionSnapshotV1 snapshot) {
        return switch (snapshot.metadata().presentation()) {
            case WITH_FIGURE -> "figure";
            case WITH_TABLE -> "table";
            case TEXT_ONLY -> "text-only";
        };
    }

    static String sourceFamily(String sourceRef, Long questionId) {
        if (sourceRef == null || sourceRef.isBlank()) return "authored:" + questionId;
        int separator = sourceRef.lastIndexOf('_');
        return separator < 0 ? sourceRef : sourceRef.substring(0, separator);
    }

    static String normalize(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replace("\r\n", "\n").replace('\r', '\n');
        return java.util.Arrays.stream(normalized.split("\\n", -1))
                .map(line -> HORIZONTAL_SPACE.matcher(line).replaceAll(" ").trim())
                .reduce((left, right) -> left + "\n" + right).orElse("")
                .replaceFirst("\\n+$", "");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte current : digest) result.append(String.format("%02x", current));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
