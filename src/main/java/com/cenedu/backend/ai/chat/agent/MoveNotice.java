package com.cenedu.backend.ai.chat.agent;

import com.cenedu.backend.ai.chat.agent.ConceptChatResult.MoveOutcome;

/**
 * 이번 턴에 앵커가 내려왔다는 사실. 근거에 실어 2차 LLM 에게 알린다.
 *
 * <p><b>이게 없으면 이동한 턴이 실패한 턴처럼 답한다.</b> task_24 에서 이동한 9턴 중 6턴이
 * "그 개념은 자료에 없어요" 로 답했다. 질문은 {@code 이항} 인데 근거는 {@code 등식과 좌변…} 이니,
 * 답변 프롬프트 규칙 1·7("자료에 있는 것만 쓴다 / 못 찾았으면 못 찾았다고 알린다")을 성실히
 * 지키면 그 문장이 나오는 것이 당연하다. 하향 탐색의 취지는 정반대다 — <b>못 찾은 것이 아니라
 * 일부러 더 쉬운 데로 내려온 것</b>이라는 사실을 근거가 말해 주지 않으면 모델이 알 길이 없다.
 *
 * <p>이동하지 않은 턴에는 {@code null} 이며 근거에 이 블록이 붙지 않는다. 붙으면 39턴 음성
 * 대조군 36턴의 근거 텍스트가 바뀌어 대조군 자격이 사라진다.
 *
 * @param askedConceptName 학생이 보고 있던 개념. 읽어 오지 못했으면 {@code null}
 * @param outcome          어떻게 내려왔는지. 한 칸인지 대역을 건너뛴 것인지를 문구가 가른다
 */
public record MoveNotice(String askedConceptName, MoveOutcome outcome) {

    /** 대역을 건너뛴 이동인가. 한 칸 내려간 것과 학생에게 할 말이 다르다. */
    public boolean isJump() {
        return outcome == MoveOutcome.LANDING;
    }
}
