package com.cenedu.backend.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CachingEmbeddingClientTest {

    private final OpenAiEmbeddingClient delegate = mock(OpenAiEmbeddingClient.class);

    @Test
    void cachesResultAndSkipsDelegateOnRepeatedText() {
        CachingEmbeddingClient caching = new CachingEmbeddingClient(delegate, 256);
        when(delegate.embed("문서 A")).thenReturn(result(1f));

        EmbeddingResult first = caching.embed("문서 A");
        EmbeddingResult second = caching.embed("문서 A");

        assertThat(second).isSameAs(first);
        verify(delegate, times(1)).embed("문서 A");
    }

    @Test
    void embedsEachDistinctTextSeparately() {
        CachingEmbeddingClient caching = new CachingEmbeddingClient(delegate, 256);
        when(delegate.embed("A")).thenReturn(result(1f));
        when(delegate.embed("B")).thenReturn(result(2f));

        caching.embed("A");
        caching.embed("B");

        verify(delegate, times(1)).embed("A");
        verify(delegate, times(1)).embed("B");
    }

    @Test
    void evictsLeastRecentlyUsedBeyondCapacity() {
        CachingEmbeddingClient caching = new CachingEmbeddingClient(delegate, 1);
        when(delegate.embed("A")).thenReturn(result(1f));
        when(delegate.embed("B")).thenReturn(result(2f));

        caching.embed("A");
        caching.embed("B"); // capacity 1 → A 축출
        caching.embed("A"); // 재계산

        verify(delegate, times(2)).embed("A");
    }

    @Test
    void delegatesBlankInputWithoutCaching() {
        CachingEmbeddingClient caching = new CachingEmbeddingClient(delegate, 256);
        when(delegate.embed("  ")).thenThrow(new IllegalArgumentException("임베딩 입력 문서는 필수입니다."));

        assertThatThrownBy(() -> caching.embed("  "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(delegate, times(1)).embed("  ");
    }

    @Test
    void failureIsNotCachedAndPropagates() {
        CachingEmbeddingClient caching = new CachingEmbeddingClient(delegate, 256);
        when(delegate.embed("문서"))
                .thenThrow(new EmbeddingCallException("실패", true))
                .thenReturn(result(9f));

        assertThatThrownBy(() -> caching.embed("문서")).isInstanceOf(EmbeddingCallException.class);
        // 실패는 캐시되지 않으므로 다음 호출은 다시 위임되어 성공한다.
        assertThat(caching.embed("문서").vector().getFirst()).isEqualTo(9f);
        verify(delegate, times(2)).embed("문서");
    }

    @Test
    void collapsesConcurrentIdenticalRequestsIntoOneCall() throws Exception {
        CachingEmbeddingClient caching = new CachingEmbeddingClient(delegate, 256);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(delegate.embed("동시")).thenAnswer(invocation -> {
            calls.incrementAndGet();
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return result(7f);
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<EmbeddingResult> first = pool.submit(() -> caching.embed("동시"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue(); // 첫 호출이 delegate에 진입
            Future<EmbeddingResult> second = pool.submit(() -> caching.embed("동시"));
            Thread.sleep(150); // 두 번째 호출이 in-flight future를 기다리도록 한다
            release.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).vector().getFirst()).isEqualTo(7f);
            assertThat(second.get(5, TimeUnit.SECONDS).vector().getFirst()).isEqualTo(7f);
            assertThat(calls.get()).isEqualTo(1); // single-flight로 delegate는 한 번만
        } finally {
            pool.shutdownNow();
        }
    }

    private static EmbeddingResult result(float value) {
        return new EmbeddingResult("text-embedding-3-small", List.of(value));
    }
}
