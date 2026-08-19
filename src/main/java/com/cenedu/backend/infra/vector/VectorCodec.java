package com.cenedu.backend.infra.vector;

import java.util.ArrayList;
import java.util.List;

public final class VectorCodec {
    private static final int DIMENSIONS = 1024;
    private VectorCodec() {}

    /** 1024차원 유한 벡터를 PostgreSQL vector literal로 인코딩한다. */
    public static String encode(List<Float> vector) {
        validate(vector);
        return vector.toString().replace(" ", "");
    }

    /** PostgreSQL vector literal을 1024차원 Float 목록으로 디코딩한다. */
    public static List<Float> decode(String vectorLiteral) {
        if (vectorLiteral == null || !vectorLiteral.startsWith("[") || !vectorLiteral.endsWith("]")) {
            throw new IllegalArgumentException("벡터 literal 형식이 올바르지 않습니다.");
        }
        String body = vectorLiteral.substring(1, vectorLiteral.length() - 1);
        List<Float> vector = new ArrayList<>();
        if (!body.isBlank()) for (String value : body.split(",")) vector.add(Float.valueOf(value));
        validate(vector);
        return List.copyOf(vector);
    }

    /** 두 유한 1024차원 벡터의 cosine similarity를 계산한다. */
    public static double cosineSimilarity(List<Float> left, List<Float> right) {
        validate(left); validate(right);
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            double l = left.get(i), r = right.get(i);
            dot += l * r; leftNorm += l * l; rightNorm += r * r;
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static void validate(List<Float> vector) {
        if (vector == null || vector.size() != DIMENSIONS
                || vector.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalArgumentException("임베딩 벡터는 유한한 1024차원이어야 합니다.");
        }
    }
}
