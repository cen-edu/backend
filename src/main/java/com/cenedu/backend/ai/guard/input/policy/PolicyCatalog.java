package com.cenedu.backend.ai.guard.input.policy;

import com.cenedu.backend.ai.agent.AgentKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 배포 시 버전 관리되는 정책 문서를 읽는다. 사용자 입력으로 정책을 등록하지 않는다. */
@Component
public class PolicyCatalog {
    private final List<Policy> policies;

    public PolicyCatalog() throws IOException {
        try (var stream = new ClassPathResource("ai/guard/chat-policies.txt").getInputStream()) {
            policies = Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("(?m)^---\\s*$"))
                    .filter(block -> !block.isBlank()).map(PolicyCatalog::parse).toList();
        }
        var ids = new HashSet<String>();
        for (Policy policy : policies) {
            if (!ids.add(policy.id())) throw new IllegalStateException("중복 정책 ID: " + policy.id());
        }
        for (String scope : List.of("COMMON", "SOLVE_CHAT", "REVIEW_CHAT")) {
            if (policies.stream().noneMatch(p -> p.scope().equals(scope))) {
                throw new IllegalStateException("필수 정책 범위 누락: " + scope);
            }
        }
    }

    /** 공통 정책과 서버가 선택한 에이전트 종류의 정책만 반환한다. */
    public List<Policy> applicable(AgentKind kind) {
        return policies.stream().filter(p -> p.scope().equals("COMMON") || p.scope().equals(kind.name())).toList();
    }

    private static Policy parse(String block) {
        String[] lines = block.strip().split("\\R", 2);
        String[] header = lines[0].split("\\|", -1);
        if (header.length != 4 || lines.length != 2 || lines[1].isBlank()
                || !SCOPES.contains(header[1]) || !List.of("ALLOW", "BLOCK").contains(header[2])
                || !header[0].matches("[A-Z]+-[0-9]+") || header[3].isBlank()) {
            throw new IllegalStateException("정책 형식 오류");
        }
        return new Policy(header[0], header[1], header[2], header[3], lines[1].strip());
    }

    private static final List<String> SCOPES = List.of("COMMON", "SOLVE_CHAT", "REVIEW_CHAT");

    public record Policy(String id, String scope, String effect, String message, String body) {
        public String document() {
            return "ID=" + id + " SCOPE=" + scope + " EFFECT=" + effect + "\n" + body;
        }
    }
}
