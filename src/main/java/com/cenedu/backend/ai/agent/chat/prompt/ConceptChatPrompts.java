package com.cenedu.backend.ai.agent.chat.prompt;

import java.util.List;

import com.cenedu.backend.domain.chat.dto.response.ConceptContext;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;

/**
 * 개념 챗봇이 쓰는 프롬프트. 고정 2단계라 프롬프트도 두 개뿐이다.
 *
 * <p>{@code ai/prompt} 는 이동규 소유라 쓰지 않는다. 한 파일에 모아 둔 이유는 두 프롬프트가
 * 짝으로만 의미를 갖기 때문이다 — 1차가 뽑은 어휘로 찾은 근거를 2차가 받는다.
 *
 * <p>프롬프트에 든 개념 이름 예시(음수·거듭제곱·이항·계수·맞꼭지각·각의 크기)는 전부 DB 의
 * {@code chat_concept.name} 에 실재하는 값이다. 없는 이름을 예시로 넣으면 모델이 그 이름을
 * 그대로 뽑아 검색이 0건으로 떨어진다.
 */
public final class ConceptChatPrompts {

    private ConceptChatPrompts() {
    }

    /**
     * 1차 — 학생 말투를 개념명 어휘로 옮긴다.
     *
     * <p>이 프롬프트의 목적은 요약이 아니라 <b>어휘 변환</b>이다. task_05 측정에서 {@code ILIKE}
     * 도달률이 가장 낮았던 것이 학생 어휘(I 분류, 40.0%)였고, "마이너스"·"3x" 처럼 개념명과
     * 글자가 겹치지 않는 말은 부분 문자열 검색으로는 원리적으로 닿지 않는다.
     *
     * <p>순서를 지시하는 이유: {@code ConceptQueryService.searchConcepts} 는 앞에서부터 두드려
     * 결과가 나오는 첫 키워드에서 멈춘다. 구체적인 것이 앞에 와야 넓은 말이 앵커를 덮지 않는다.
     */
    public static final String KEYWORD_EXTRACTION = """
            너는 중학교 1학년 수학 교육과정에서 개념 이름을 찾아 주는 도구다.
            학생의 질문을 읽고, 그 질문에 답하는 데 필요한 개념 이름을 뽑는다.

            규칙
            1. JSON 배열만 출력한다. 설명도, 코드펜스도, 앞뒤 문장도 붙이지 않는다.
            2. 2~3개를 뽑는다. 구체적인 것을 앞에, 일반적인 것을 뒤에 둔다.
               앞의 것으로 찾으면 뒤의 것은 쓰이지 않는다.
            3. 학생이 쓴 말을 교과서에 나오는 용어로 바꾼다.
               "마이너스" -> "음수", "제곱" -> "거듭제곱", "넘긴다" -> "이항", "3x의 3" -> "계수"
            4. 중학교 1학년 수학과 관계없는 질문이면 빈 배열 [] 을 출력한다.

            예시
            질문: 맞꼭지각이 왜 항상 같아요?
            ["맞꼭지각","각의 크기"]

            질문: 오늘 급식 뭐예요?
            []""";

    /**
     * 2차 — 조회한 근거만으로 설명한다.
     *
     * <p>길이를 5문장으로 제한한 근거: (1) 문제를 푸는 중에 뜨는 말풍선이라 길면 학생이 읽지 않고,
     * (2) 출력 토큰을 한도(3000)에서 크게 떨어뜨려 두어야 추론 토큰이 붙어도 {@code length} 로
     * 잘리지 않는다. task_07 에서 잘림은 곧 빈 응답이 된다는 것을 확인했다.
     */
    private static final String ANSWER_GENERATION = """
            너는 중학교 1학년 학생에게 수학 개념을 설명하는 선생님이다.

            규칙
            1. 아래 [개념 자료] 에 있는 내용만 근거로 쓴다. 자료에 없는 내용을 지어내지 않는다.
            2. 자료가 질문에 답하기 부족하면 모른다고 말하고, 무엇을 물어보면 되는지 알려 준다.
            3. 문제의 답을 직접 알려주지 않는다. 학생은 지금 문제를 푸는 중이다. 개념만 설명한다.
            4. 수식은 받은 LaTeX 표기를 그대로 쓴다. 다른 형태로 바꾸지 않는다.
            5. 5문장 이내로, 중학교 1학년이 아는 말로 답한다.""";

    /** 근거가 전무할 때 돌려주는 고정 문구. 이 경우 2차 LLM 을 부르지 않는다. */
    public static final String NO_EVIDENCE_ANSWER = """
            지금 가진 개념 자료에서는 관련된 내용을 찾지 못했어요.
            교과서에 나오는 말로 다시 물어봐 주세요. 예를 들면 "맞꼭지각이 뭐예요?", \
            "소인수분해를 왜 해요?" 처럼 개념 이름을 넣어 물어보면 찾을 수 있어요.""";

    /**
     * 답변 프롬프트 뒤에 조회한 근거를 붙여 2차 시스템 프롬프트를 만든다.
     *
     * <p>{@code description} 은 {@link ConceptView} 가 이미 리터럴 {@code \\n} 을 실제 개행으로
     * 바꿔 둔 상태다. 여기서 다시 치환하지 않는다. 치환 지점이 둘로 늘면 어느 쪽이 바꿨는지
     * 추적할 수 없다.
     */
    public static String answerSystemPrompt(ConceptContext context) {
        StringBuilder prompt = new StringBuilder(ANSWER_GENERATION)
                .append("\n\n[개념 자료]\n");

        ConceptView anchor = context.anchor();
        if (anchor != null) {
            prompt.append("\n[앵커 개념]\n")
                    .append("이름: ").append(anchor.name()).append('\n')
                    .append("설명: ").append(anchor.description()).append('\n');
        }

        // concepts 의 첫 원소는 앵커 자신(hop 0)이라 선수 개념에서는 뺀다.
        List<ConceptView> prereqs = context.concepts().stream()
                .filter(concept -> concept.hop() != null && concept.hop() > 0)
                .toList();
        if (!prereqs.isEmpty()) {
            prompt.append("\n[선수 개념]\n");
            for (ConceptView prereq : prereqs) {
                prompt.append("- ").append(prereq.name()).append(": ")
                        .append(prereq.description()).append('\n');
            }
        }

        if (!context.subUnitConceptNames().isEmpty()) {
            prompt.append("\n[이 단원의 개념 목록]\n")
                    .append(String.join(", ", context.subUnitConceptNames()))
                    .append('\n');
        }

        return prompt.toString();
    }
}
