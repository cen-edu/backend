package com.cenedu.backend.ai.guard.input.policy;

import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.embedding.EmbeddingClient;
import com.cenedu.backend.ai.embedding.EmbeddingResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 현재 파일 버전만 pgvector에 동기화하고 공통/페이지별 top-k를 검색한다. */
@Component
public class PolicyRetriever {
    private final PolicyCatalog catalog;
    private final EmbeddingClient embeddings;
    private final PolicyVectorRepository repository;
    private final int topK;

    public PolicyRetriever(PolicyCatalog catalog, EmbeddingClient embeddings, PolicyVectorRepository repository,
            @Value("${app.ai.guard.policy.top-k-per-scope:3}") int topK) {
        if (topK < 1 || topK > 20) throw new IllegalArgumentException("정책 top-k는 1~20이어야 합니다.");
        this.catalog = catalog;
        this.embeddings = embeddings;
        this.repository = repository;
        this.topK = topK;
    }

    /** 해당 페이지 정책을 영속 동기화한 뒤 DB에서 코사인 유사도로 검색한다. */
    public List<Hit> retrieve(AgentKind kind, String query) {
        if (kind != AgentKind.SOLVE_CHAT && kind != AgentKind.REVIEW_CHAT) {
            throw new IllegalArgumentException("정책 검색은 학생 채팅에만 적용합니다.");
        }
        EmbeddingResult question = embeddings.embed(query);
        String queryVector = encode(question);
        List<Version> versions = catalog.applicable(kind).stream().map(policy -> version(policy, question.model())).toList();
        synchronize(versions, question.model());
        List<String> keys = versions.stream().map(Version::key).toList();
        List<Hit> selected = new ArrayList<>();
        for (String scope : List.of("COMMON", kind.name())) {
            List<Hit> hits = repository.search(keys, scope, question.model(), queryVector, topK);
            if (hits.isEmpty()) throw new IllegalStateException("필수 범위의 정책 검색 결과가 없습니다.");
            selected.addAll(hits);
        }
        return List.copyOf(selected);
    }

    /** 프로세스 내 초기 임베딩 중복을 막고 이미 저장된 버전은 재사용한다. */
    private synchronized void synchronize(List<Version> versions, String model) {
        var existing = repository.existing(versions.stream().map(Version::key).toList());
        for (Version version : versions) {
            if (existing.contains(version.key())) continue;
            EmbeddingResult result = embeddings.embed(version.policy().document());
            if (!model.equals(result.model())) throw new IllegalStateException("정책과 질문 임베딩 모델이 다릅니다.");
            repository.insert(version.key(), version.hash(), model, version.policy(), encode(result));
        }
    }

    private static Version version(PolicyCatalog.Policy policy, String model) {
        String hash = hash(policy.document() + "\nMESSAGE\n" + policy.message());
        return new Version(policy, hash, hash(hash + "\n" + model + "\n1024"));
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static String encode(EmbeddingResult result) {
        if (result.model().isBlank() || result.vector().size() != 1024) {
            throw new IllegalStateException("정책 벡터는 모델과 1024차원이 필요합니다.");
        }
        double norm = 0;
        for (Float value : result.vector()) {
            if (value == null || !Float.isFinite(value)) throw new IllegalStateException("유효하지 않은 정책 벡터");
            norm += (double) value * value;
        }
        if (norm == 0) throw new IllegalStateException("영벡터는 검색할 수 없습니다.");
        return result.vector().stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private record Version(PolicyCatalog.Policy policy, String hash, String key) {}
    public record Hit(PolicyCatalog.Policy policy, double score) {}
}
