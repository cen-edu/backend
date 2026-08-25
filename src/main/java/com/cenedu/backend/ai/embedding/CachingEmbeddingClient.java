package com.cenedu.backend.ai.embedding;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 임베딩은 (문서 텍스트, 모델, 차원) 고정 시 결정적이므로, 같은 문서의 재임베딩 API 호출을 줄이는
 * 데코레이터다.
 *
 * <ul>
 *   <li><b>single-flight</b>: 동시에 들어온 동일 텍스트 요청을 하나의 계산으로 합친다. 계획 단계에서
 *       ADVANCED 검색이 소단원마다 병렬로 같은 origin을 임베딩하는 경우(P2 병렬화)를 한 번으로 줄인다.
 *       계산이 실패하면 대기자에게 같은 예외를 전파한다 — 어차피 같은 입력이라 결과가 같다.</li>
 *   <li><b>LRU 캐시</b>: 완료된 임베딩을 크기 제한 LRU로 보관해, 교차 요청·시간차 재임베딩을 재사용한다.
 *       모델 고정 시 결정적이므로 TTL은 두지 않는다(모델 변경은 재기동을 수반한다).</li>
 * </ul>
 *
 * <p>인덱싱 경로도 이 {@code @Primary} 빈을 통하지만, 인덱싱 문서는 문항마다 텍스트가 달라 single-flight가
 * 자연히 무효이고, LRU를 조금 채울 뿐이라 검색 경로의 이득을 해치지 않는다.
 */
@Primary
@Component
public class CachingEmbeddingClient implements EmbeddingClient {

    private final EmbeddingClient delegate;
    private final Map<String, EmbeddingResult> cache;
    private final ConcurrentHashMap<String, CompletableFuture<EmbeddingResult>> inFlight = new ConcurrentHashMap<>();

    public CachingEmbeddingClient(OpenAiEmbeddingClient delegate,
            @Value("${app.ai.embedding.cache-size:256}") int maxSize) {
        this.delegate = delegate;
        int capacity = Math.max(1, maxSize);
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, EmbeddingResult> eldest) {
                return size() > capacity;
            }
        });
    }

    @Override
    public EmbeddingResult embed(String text) {
        // 빈 입력 검증·예외는 위임에 맡긴다. 캐시/합치기 대상이 아니다.
        if (text == null || text.isBlank()) {
            return delegate.embed(text);
        }
        EmbeddingResult cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<EmbeddingResult> future = new CompletableFuture<>();
        CompletableFuture<EmbeddingResult> running = inFlight.putIfAbsent(text, future);
        if (running != null) {
            // 다른 스레드가 같은 텍스트를 이미 계산 중이다. 그 결과를 공유한다.
            return join(running);
        }
        try {
            EmbeddingResult result = delegate.embed(text);
            cache.put(text, result);
            future.complete(result);
            return result;
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(text, future);
        }
    }

    private EmbeddingResult join(CompletableFuture<EmbeddingResult> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw exception;
        }
    }
}
