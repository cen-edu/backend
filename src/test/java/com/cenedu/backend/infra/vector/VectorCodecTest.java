package com.cenedu.backend.infra.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class VectorCodecTest {
    @Test
    void encodesDecodesAndCalculatesCosine() {
        List<Float> vector = java.util.stream.IntStream.range(0, 1024).mapToObj(i -> 1f).toList();
        assertThat(VectorCodec.decode(VectorCodec.encode(vector))).containsExactlyElementsOf(vector);
        assertThat(VectorCodec.cosineSimilarity(vector, vector)).isEqualTo(1.0);
    }

    @Test
    void rejectsWrongDimensionAndNonFiniteValues() {
        assertThatThrownBy(() -> VectorCodec.encode(List.of(1f)))
                .isInstanceOf(IllegalArgumentException.class);
        List<Float> invalid = java.util.stream.IntStream.range(0, 1023).mapToObj(i -> 1f).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        invalid.add(Float.NaN);
        assertThatThrownBy(() -> VectorCodec.encode(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
