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
     * 1차 — 질문에 있는 말을 그대로 살리고, 거기에 교과 용어를 덧붙인다.
     *
     * <p><b>목적이 바뀌었다.</b> 처음에는 "학생 말투를 개념명으로 옮기는 것" 하나였는데,
     * task_08 측정에서 그 규칙이 <b>항상 번역하려 드는</b> 부작용을 냈다. 질문에 개념명이 그대로
     * 들어 있는데도 다른 말로 바꾸거나(I5 "표" → "열"), 아예 빈 배열을 냈다(I4 "꼬인 위치").
     * 그래서 원문을 첫 번째로 살리고 번역어를 뒤에 붙이는 형태로 바꿨다.
     *
     * <p>"둘 다 넣어도 된다" 를 명시하는 것이 중요하다. 하나만 골라야 한다고 생각하면 모델이
     * 원문을 버린다. 검색이 병합 랭킹이라 후보가 늘어도 손해가 없다.
     *
     * <p><b>예시는 평가 세트에 없는 개념으로 고른다.</b> 처음에는 실패 사례였던 "꼬인 위치" 와
     * "마이너스 곱하기 마이너스" 를 그대로 예시로 썼는데, 그건 평가 문항의 정답을 프롬프트에
     * 심어 두는 것이라 측정이 무의미해진다. 부채꼴·원은 평가 세트에 나오지 않는다.
     *
     * <p>예전에 있던 <b>"구체적인 것부터 일반적인 것 순서로" 지시는 뺐다.</b> 첫 히트에서 멈추던
     * 시절의 규칙이라 지금은 무의미하고(task_09), 모델이 그 지시를 지키려다 원문을 뒤로 민다.
     */
    public static String keywordExtraction(List<String> subUnitConceptNames) {
        StringBuilder prompt = new StringBuilder("""
                너는 중학교 1학년 수학 교육과정에서 개념 이름을 찾아 주는 도구다.
                학생의 질문을 읽고, 그 질문에 답하는 데 필요한 개념 이름을 뽑는다.

                규칙
                1. JSON 배열만 출력한다. 설명도, 코드펜스도, 앞뒤 문장도 붙이지 않는다.
                2. 질문에 교과서에 나올 법한 말이 이미 들어 있으면, 그 말을 바꾸지 말고
                   그대로 첫 번째에 넣는다.
                3. 그다음에 학생이 쓴 말을 교과서 용어로 옮긴 것을 넣는다.
                   "마이너스" -> "음수", "제곱" -> "거듭제곱", "넘긴다" -> "이항", "3x의 3" -> "계수"
                4. 둘 다 있으면 둘 다 넣는다. 하나만 고르지 않는다.
                   어느 쪽이 맞는지는 검색이 알아서 고른다.
                5. 중학교 1학년 수학과 관계없는 질문이면 빈 배열 [] 을 출력한다.

                예시
                질문: 부채꼴이 뭔지 잘 모르겠어요
                ["부채꼴","원의 일부분"]

                질문: 동그라미 둘레 구하는 거요
                ["원","원주율"]

                질문: 오늘 급식 뭐예요?
                []""");

        if (subUnitConceptNames != null && !subUnitConceptNames.isEmpty()) {
            // 실제 개념명을 보여 주면 DB 에 없는 이름을 지어내는 일이 줄어든다.
            // task_08 에서 "음수의 곱셈"·"열"·"지수의 뜻" 처럼 그럴듯하지만 없는 이름이 나왔다.
            //
            // "목록 밖도 된다" 를 반드시 붙인다. C 분류(교차 소단원)는 앵커가 이 소단원 밖에
            // 있는 질문들이고 4문항 중 3건이 이미 성공한다. 과하게 묶으면 그걸 잃는다.
            prompt.append("\n\n참고 — 학생이 지금 보고 있는 단원의 개념 이름\n")
                    .append(String.join(", ", subUnitConceptNames))
                    .append("\n이건 참고용 목록이다. 다른 단원의 개념을 물을 수도 있으니")
                    .append(" 목록에 없는 이름을 뽑아도 된다.");
        }

        return prompt.toString();
    }

    /**
     * 2차 — 조회한 근거만으로 설명한다.
     *
     * <p>길이를 5문장으로 제한한 근거: (1) 문제를 푸는 중에 뜨는 말풍선이라 길면 학생이 읽지 않고,
     * (2) 출력 토큰을 한도(3000)에서 크게 떨어뜨려 두어야 추론 토큰이 붙어도 {@code length} 로
     * 잘리지 않는다. task_07 에서 잘림은 곧 빈 응답이 된다는 것을 확인했다.
     *
     * <p>규칙 7 의 배경: 규칙 1·2 만 있을 때 범위 밖 개념을 물으면 "자료에 없어서 모른다" 로만
     * 답했고, 학생에게는 그것이 "챗봇이 고장났다" 와 구별되지 않았다(task_11 에서 0/2).
     *
     * <p><b>그렇다고 "자료에 없으면 범위 밖" 이라고 추론하게 두면 안 된다.</b> 우리 455개 개념은
     * 중1 전 과정을 덮지 못한다 — {@code 미지수} 는 일차방정식의 핵심 용어인데 개념 이름으로 없고,
     * {@code 약분} 은 짝인 {@code 통분} 만 있다. 그 추론을 허용하면 모델이 "미지수는 중1 에서 안
     * 배워요" 라고 <b>학생에게 거짓을 말한다.</b> 그래서 단정을 금지하고 두 가능성을 함께 알리게 한다.
     */
    private static final String ANSWER_GENERATION = """
            너는 중학교 1학년 학생에게 수학 개념을 설명하는 선생님이다.

            규칙
            1. 아래 [개념 자료] 에 있는 내용만 근거로 쓴다. 자료에 없는 내용을 지어내지 않는다.
            2. 자료가 질문에 답하기 부족하면 모른다고 말하고, 무엇을 물어보면 되는지 알려 준다.
            3. 문제의 답을 직접 알려주지 않는다. 학생은 지금 문제를 푸는 중이다. 개념만 설명한다.
            4. 수식은 받은 LaTeX 표기를 그대로 쓴다. 다른 형태로 바꾸지 않는다.
            5. 5문장 이내로, 중학교 1학년이 아는 말로 답한다.
            6. [이 단원의 개념 목록] 은 이름만 있고 설명이 없다. 그 이름을 보고 뜻이나 성질을
               서술하지 않는다. 그 이름들은 "무엇을 물어보면 되는지" 안내할 때만 쓴다.
               설명을 쓸 수 있는 것은 [앵커 개념] 과 [선수 개념] 뿐이다.
            7. 학생이 물은 개념을 자료에서 찾지 못했으면, 못 찾았다는 것만 말하지 말고
               "아직 배우지 않는 내용이거나, 내 자료에 없는 개념일 수 있다" 고 두 가능성을
               함께 알린다. 그다음 무엇을 물어보면 되는지 안내한다.
            8. 어느 쪽인지 단정하지 않는다. 어느 학년에서 배우는지는 자료에 없으므로 알 수 없다.
               "그건 중2 에서 배워요", "고등학교 과정이에요" 처럼 학년을 못 박는 문장은 쓰지 않는다.""";

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
