package com.cenedu.backend.ai.chat.agent;

import java.util.List;

import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.domain.chat.dto.response.ConceptContext;

/**
 * 파이프라인 한 번의 결과. {@code SolveChatAgent} 는 이 중 {@link #text()} 만 쓴다.
 *
 * <p>중간값까지 같이 돌려주는 이유는 <b>실패 지점을 분리해 측정하기 위해서</b>다. 키워드 추출이
 * 나쁜 것과 검색이 못 닿은 것과 생성이 근거를 무시한 것은 고칠 곳이 다른데, 텍스트만 돌려주면
 * 로그를 파싱하지 않고는 셋을 구분할 수 없다.
 *
 * @param keywords    1차 LLM 이 뽑은 키워드. 파싱에 실패했으면 빈 리스트
 * @param moveState   1차 LLM 이 낸 학생 상태 판정
 * @param moveOutcome 그 판정으로 코드가 실제로 한 이동
 * @param context     조회한 근거. 이동이 일어났으면 <b>도착 개념 기준</b>이다
 * @param generation  2차 LLM 응답. 근거가 전무하거나 갈 곳이 없어 호출하지 않았으면 {@code null}
 */
public record ConceptChatResult(
        String text,
        List<String> keywords,
        KeywordParse keywordParse,
        MoveState moveState,
        MoveOutcome moveOutcome,
        ConceptContext context,
        LlmResponse generation
) {

    /** 1차 응답을 JSON 으로 읽어낸 방식. 모델이 형식을 어기는 빈도를 재려고 남긴다. */
    public enum KeywordParse {
        /** 지시대로 JSON 객체만 왔다. */
        PARSED,
        /** 코드펜스가 붙어 와서 벗겨낸 뒤 읽었다. */
        PARSED_AFTER_FENCE_STRIP,
        /** 어느 쪽으로도 읽지 못해 키워드 없이 진행했다. */
        FAILED,
    }

    /**
     * 학생이 지금 무엇을 원하는지. 1차 LLM 이 키워드와 함께 낸다.
     *
     * <p>이름을 {@code DownwardEvalQuestions.MoveState} 와 맞춘 것은 의도한 것이다. 평가 세트의
     * 기대 상태와 실제 판정을 대조할 때 이름이 다르면 대응표를 하나 더 들고 다녀야 한다.
     */
    public enum MoveState {
        /**
         * <b>새 질문이다.</b> 앵커를 움직이지 않고, 되받은 앵커도 쓰지 않는다 — 앵커는 이번 질문이 정한다.
         *
         * <p>판단이 서지 않을 때의 기본값이기도 하다. {@link #HOLD} 를 기본값으로 두면 화제 전환이 죽는다.
         */
        NONE,
        /**
         * <b>방금 설명한 개념에 대해 말하고 있다</b> — 알아들었다 · 고맙다 · 한 번 더 설명해 달라.
         * 되받은 앵커를 그대로 지키고 이동 조회를 부르지 않는다.
         *
         * <p><b>{@code NONE} 에서 떼어낸 이유(task_24c §0-2).</b> 둘은 "이동하지 않는다" 는 같지만
         * <b>앵커를 어떻게 하느냐가 반대</b>다. 하나로 묶여 있던 동안 `NONE` 의 재검색 규칙이
         * 화제 전환(`DC3-2`)을 살리고 이해 표현(`DA1-3`)을 죽였다 — 되받은 앵커가 맞는데도 버리고
         * 키워드로 다시 찾아 대화 첫 개념으로 되감겼다(세 실행 모두 같은 자리).
         *
         * <p>되받은 앵커가 없으면(첫 턴 등) 키워드로 찾은 앵커를 쓴다. {@code NONE} 으로 강등하지 않는다 —
         * 행동은 같아도 로그에서 두 경우를 갈라 볼 수 있어야 한다.
         */
        HOLD,
        /** 방금 설명이 어렵다. 직접 선수 1순위로 한 칸 내려간다. */
        SLIGHTLY_EASIER,
        /** 기초부터 다시. 가장 가까운 초등 개념으로 건너뛴다. */
        MUCH_EASIER,
    }

    /**
     * 판정을 받아 코드가 실제로 한 일. 판정과 따로 남기는 이유는 <b>둘이 갈리는 지점이
     * 곧 그래프의 한계</b>이기 때문이다 — 학생이 건너뛰기를 요청해도 초등에 닿는 경로가
     * 없으면 한 칸으로 내려가고, 선수가 하나도 없으면 아무 데도 못 간다.
     */
    public enum MoveOutcome {
        /** 판정이 {@code NONE} 이거나 앵커가 없어 이동을 시도하지 않았다. */
        NOT_REQUESTED,
        /** 1순위 선수로 한 칸 내려갔다. */
        STEP_DOWN,
        /** 최단 초등 개념으로 건너뛰었다. */
        LANDING,
        /** 건너뛰기를 요청받았지만 착지가 없어 한 칸으로 내려갔다. */
        FALLBACK_STEP_DOWN,
        /** 선수가 없어 내려가지 못했다. 물러섬 문구로 답한다. */
        DEAD_END,
    }

    /** 근거가 전무해 2차 LLM 을 부르지 않은 경우. */
    public static ConceptChatResult noEvidence(String text, List<String> keywords,
                                               KeywordParse keywordParse, MoveState moveState,
                                               ConceptContext context) {
        return new ConceptChatResult(
                text, keywords, keywordParse, moveState, MoveOutcome.NOT_REQUESTED, context, null);
    }

    /** 하향 요청을 받았지만 앵커에 선수가 없어 2차 LLM 을 부르지 않은 경우. */
    public static ConceptChatResult deadEnd(String text, List<String> keywords,
                                            KeywordParse keywordParse, MoveState moveState,
                                            ConceptContext context) {
        return new ConceptChatResult(
                text, keywords, keywordParse, moveState, MoveOutcome.DEAD_END, context, null);
    }
}
