package com.cenedu.backend.ai.problem.adapter;

import java.util.List;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairCommand;
import tools.jackson.databind.ObjectMapper;

/** 검증 Finding을 한 번의 묶음 필드 수정 요청으로 변환한다. */
public class ProblemRepairPromptFactory {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 수정 대상 외 Snapshot 필드를 변경하지 않는 시스템 프롬프트를 만든다. */
    public List<ChatMessage> messages(ProblemRepairCommand command) {
        try {
            return List.of(ChatMessage.user("PROBLEM_REPAIR_REQUEST\n" + objectMapper.writeValueAsString(command)));
        } catch (Exception exception) {
            throw new IllegalStateException("문제 부분 수정 요청을 만들 수 없습니다.", exception);
        }
    }

    public String systemPrompt() {
        return """
                당신은 검증 실패 문항의 부분 수정기다.
                반드시 JSON 객체만 출력하고 replacements와 rationale을 포함하라.
                replacements에는 요청된 target만 포함하고 다른 Snapshot 필드는 절대 출력하지 마라.
                문제 유형, 난이도, 교육과정, 발문 원문 등 요청되지 않은 값은 변경하지 마라.
                기존 정답·보기·풀이·해설의 의미가 서로 일치하도록 수정하되, 요청된 필드만 수정하라.
                """;
    }
}
