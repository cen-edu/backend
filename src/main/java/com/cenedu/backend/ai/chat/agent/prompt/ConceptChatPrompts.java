package com.cenedu.backend.ai.chat.agent.prompt;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cenedu.backend.ai.chat.agent.MoveNotice;
import com.cenedu.backend.domain.chat.dto.response.ConceptContext;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;
import com.cenedu.backend.domain.chat.entity.enums.GradeBand;

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

    /** {@code source_semester} 에서 초등 학년만 읽는다. 예: {@code 초등-초4-1학기} → {@code 4} */
    private static final Pattern ELEMENTARY_GRADE = Pattern.compile("초([1-6])-");

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
     *
     * <p><b>출력이 배열에서 객체로 바뀌었다</b> — 키워드와 함께 학생 상태를 낸다. 판정을 별도
     * 호출로 떼지 않은 것은 두 판단의 재료가 같기 때문이다. 같은 발화와 같은 이력을 두 번
     * 보내면 비용이 두 배가 되고, 두 호출의 해석이 갈리면 어느 쪽을 믿을지가 또 문제가 된다.
     *
     * <p><b>state 예시에 평가 세트의 발화를 쓰지 않는다.</b> task_10 에서 평가 문항을 프롬프트
     * 예시로 넣어 정답을 심은 적이 있다. 하향 신호는 표현이 몇 가지 안 되어 완전히 겹치지 않게
     * 쓰기 어렵지만, 최소한 두 평가 세트에 실제로 있는 문장은 한 줄도 그대로 쓰지 않았다.
     *
     * <p>규칙 8 이 없으면 안 된다. 하향 신호가 있는 턴은 대개 지시어뿐이라("그거 어려워요")
     * 모델이 키워드를 비우는데, <b>어디서부터 내려갈지는 앵커가 있어야 정해진다.</b>
     * 키워드가 비면 앵커가 없고, 앵커가 없으면 판정이 맞아도 이동할 수가 없다.
     *
     * <p><b>규칙 6·7 이 "왜" 질문을 {@code NONE} 쪽으로 확정했다(task_24b §0-3).</b> task_24 에서
     * 흔들린 세 턴({@code I1}·{@code DA3-2}·{@code DA3-3})이 전부 "왜 …?" 형태였고 실행마다
     * {@code NONE} 과 {@code SLIGHTLY_EASIER} 를 오갔다. 프롬프트 표현의 문제가 아니라
     * <b>설계가 정하지 않은 것</b>이었다 — 옛 규칙 6 의 "그 바로 앞에 있는 것을 물을 때" 가
     * "왜" 질문을 하향 쪽으로 당기고 규칙 7 의 "판단이 서지 않으면 NONE" 이 반대로 밀었다.
     * "왜 그래요?" 는 <b>더 쉬운 설명이 아니라 이유</b>를 원하는 것이고, 그건 답변 형식의
     * {@code 왜/어떻게} 칸이 받을 일이지 앵커를 내릴 일이 아니다. 그래서 {@code SLIGHTLY_EASIER}
     * 를 <b>어렵다는 직접 표현</b>만 받도록 좁히고, 당기던 문구를 뺐다.
     *
     * <p><b>그 축소가 너무 넓었다(task_24c §0-1·§0-2).</b> 옛 규칙 6 의 "<b>방금 설명이</b> 어렵다" 는
     * 직전 턴을 가리키는 시간 한정이었는데 그것까지 함께 떨어졌고, 그래서 대화 <b>첫 발화</b>의
     * "정비례가 뭔지 하나도 모르겠어요" 가 하향 신호로 읽혔다(39턴 대조군 2턴). 시간 한정을
     * 되살렸지만 <b>보장은 프롬프트가 아니라 코드 게이트가 한다</b>
     * ({@code ConceptChatEngine.withoutFirstUtteranceDescent}).
     *
     * <p><b>{@code HOLD} 를 넷째 값으로 더했다.</b> "알아들었다 · 한 번 더 설명해 달라" 는 이동은
     * 없지만 <b>앵커를 지켜야</b> 하고, "화제를 바꾼다" 는 이동도 없고 <b>앵커를 새로 찾아야</b> 한다.
     * 둘이 {@code NONE} 하나에 묶여 있는 동안 재검색 규칙이 후자를 살리고 전자를 죽였다.
     * <b>규칙 7 의 기본값은 여전히 {@code NONE} 이다</b> — {@code HOLD} 가 기본이 되면 화제 전환이 죽는다.
     */
    public static String keywordExtraction(List<String> subUnitConceptNames) {
        StringBuilder prompt = new StringBuilder("""
                너는 중학교 1학년 수학 교육과정에서 개념 이름을 찾아 주는 도구다.
                학생의 질문을 읽고, 그 질문에 답하는 데 필요한 개념 이름을 뽑는다.
                함께, 학생이 지금 더 쉬운 설명을 원하는지도 판정한다.

                규칙
                1. JSON 객체만 출력한다. 설명도, 코드펜스도, 앞뒤 문장도 붙이지 않는다.
                   형식: {"keywords":[...],"state":"..."}
                2. 질문에 교과서에 나올 법한 말이 이미 들어 있으면, 그 말을 바꾸지 말고
                   그대로 첫 번째에 넣는다.
                3. 그다음에 학생이 쓴 말을 교과서 용어로 옮긴 것을 넣는다.
                   "마이너스" -> "음수", "제곱" -> "거듭제곱", "넘긴다" -> "이항", "3x의 3" -> "계수"
                4. 둘 다 있으면 둘 다 넣는다. 하나만 고르지 않는다.
                   어느 쪽이 맞는지는 검색이 알아서 고른다.
                5. 중학교 1학년 수학과 관계없는 질문이면 keywords 를 빈 배열 [] 로 둔다.
                6. state 는 학생이 지금 무엇을 원하는지다. 넷 중 하나를 고른다.
                   "NONE"            새로 묻는 경우. 개념을 묻거나, 이유나 방법을 묻거나,
                                     화제를 바꿀 때.
                   "HOLD"            방금 설명한 개념에 대해 말하고 있는 경우. 알아들었다고
                                     하거나, 고맙다고 하거나, 같은 것을 한 번 더 청할 때.
                   "SLIGHTLY_EASIER" 방금 설명이 어렵다, 모르겠다, 이해가 안 된다고 직접 말할 때.
                   "MUCH_EASIER"     훨씬 아래에서, 기초부터, 처음부터 다시 시작해 달라고 할 때.
                7. "왜" 나 "어떻게" 를 묻는 질문은 이유나 방법을 원하는 것이지 더 쉬운 설명을
                   원하는 것이 아니다. "NONE" 이다.
                   개념을 처음 묻는 질문도 "NONE" 이다. 판단이 서지 않아도 "NONE" 이다.
                8. state 가 "NONE" 이 아니어도 keywords 를 비우지 않는다. 지금까지 이야기하던
                   개념의 이름을 이력에서 찾아 넣는다. 그 이름이 있어야 어디서부터 내려갈지 정해진다.

                예시
                질문: 부채꼴이 뭔지 잘 모르겠어요
                {"keywords":["부채꼴","원의 일부분"],"state":"NONE"}

                질문: 동그라미 둘레 구하는 거요
                {"keywords":["원","원주율"],"state":"NONE"}

                앞에서 "부채꼴" 을 설명한 뒤 — 질문: 방금 그거 너무 어려운데요
                {"keywords":["부채꼴"],"state":"SLIGHTLY_EASIER"}

                앞에서 "원주율" 을 설명한 뒤 — 질문: 초등학교 때 배운 데까지 내려가 주세요
                {"keywords":["원주율"],"state":"MUCH_EASIER"}

                앞에서 "부채꼴" 을 설명한 뒤 — 질문: 아하 그렇구나
                {"keywords":["부채꼴"],"state":"HOLD"}

                질문: 오늘 급식 뭐예요?
                {"keywords":[],"state":"NONE"}""");

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
               "그건 중2 에서 배워요", "고등학교 과정이에요" 처럼 학년을 못 박는 문장은 쓰지 않는다.
            9. 개념 이름 뒤에 배우는 시기가 적혀 있으면 그 학년은 말해도 된다.
               적혀 있지 않은 개념의 학년은 여전히 알 수 없다.

            답변 형식
            아래 칸을 순서대로 쓴다. 근거가 있는 칸만 쓰고, 근거가 없는 칸은 제목째로 뺀다.
              정의             [앵커 개념] 의 설명으로 답한다.
              왜/어떻게        자료에 이유나 방법이 있을 때만 쓴다.
              알아두면 좋은 것  [선수 개념] 이 있을 때만 쓴다.
            칸을 채우려고 자료에 없는 말을 만들지 않는다. 쓸 것이 없으면 그 칸은 빼는 것이 맞다.""";

    /**
     * 하향 요청을 받았지만 앵커에 선수가 하나도 없을 때 돌려주는 고정 문구.
     * 이 경우 2차 LLM 을 부르지 않는다.
     *
     * <p><b>LLM 을 부르지 않는 것이 이 문구의 핵심이다.</b> 요건 (2) "없는 개념을 지어내지
     * 않는다" 는 모델에게 시켜서는 보장되지 않는다 — 여기 걸리는 19개는 그래프상 아래가
     * 비어 있는 개념들이고, 모델은 자기 지식으로 그 빈자리를 채울 수 있다. 고정 문구면
     * 지어낼 여지가 아예 없다.
     *
     * <p>요건 (1) 더 내려갈 개념이 없음을 알린다 · (2) 없는 개념을 지어내지 않는다 ·
     * (3) 다른 질문을 유도한다 — 셋을 이 세 문장이 각각 맡는다.
     */
    public static final String CANNOT_MOVE_ANSWER = """
            이 개념보다 더 기초가 되는 개념은 내 자료에 들어 있지 않아요.
            그래서 여기서 더 내려가서 설명해 주기는 어려워요.
            대신 지금 설명에서 어느 부분이 막히는지 짚어서 물어봐 주면, 그 부분을 다시 풀어서 알려줄게요.""";

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
        return answerSystemPrompt(context, null);
    }

    /**
     * 이동이 일어난 턴이면 그 사실을 근거 맨 앞에 실어 보낸다.
     *
     * <p><b>이 블록은 이동한 턴에만 붙는다.</b> {@code notice} 가 {@code null} 이면 문자열이
     * 위 1인자 형태와 완전히 같다 — 39턴 음성 대조군 36턴이 그 경로를 타고, 거기서 근거가
     * 달라지면 대조군 자격이 사라진다.
     *
     * <p>지시를 {@code ANSWER_GENERATION} 의 규칙 목록에 상수로 넣지 않고 이 블록 안에 둔 것도
     * 같은 이유다. 규칙으로 넣으면 이동하지 않은 턴의 프롬프트까지 바뀐다.
     */
    public static String answerSystemPrompt(ConceptContext context, MoveNotice notice) {
        StringBuilder prompt = new StringBuilder(ANSWER_GENERATION)
                .append("\n\n[개념 자료]\n");

        ConceptView anchor = context.anchor();
        if (notice != null && notice.askedConceptName() != null && anchor != null) {
            prompt.append(moveBlock(notice, anchor));
        }
        if (anchor != null) {
            prompt.append("\n[앵커 개념]\n")
                    .append("이름: ").append(named(anchor)).append('\n')
                    .append("설명: ").append(anchor.description()).append('\n');
        }

        // concepts 의 첫 원소는 앵커 자신(hop 0)이라 선수 개념에서는 뺀다.
        List<ConceptView> prereqs = context.concepts().stream()
                .filter(concept -> concept.hop() != null && concept.hop() > 0)
                .toList();
        if (!prereqs.isEmpty()) {
            prompt.append("\n[선수 개념]\n");
            for (ConceptView prereq : prereqs) {
                prompt.append("- ").append(named(prereq)).append(": ")
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

    /**
     * 이동 사실을 근거로 적는다. <b>모델은 이 문장이 없으면 이동한 턴을 실패한 턴으로 읽는다</b> —
     * 질문은 {@code 이항} 인데 근거는 {@code 등식과 좌변…} 이니, 규칙 1·7 을 성실히 지키면
     * "네가 물은 것은 자료에 없다" 가 나온다(task_24: 이동 9턴 중 6턴).
     *
     * <p>"물은 개념" 을 읽어 오지 못했으면 블록을 아예 붙이지 않는다. 무엇에서 내려왔는지를
     * 말하지 못하는 이동 통보는 모델에게 근거가 아니라 잡음이다.
     */
    private static String moveBlock(MoveNotice notice, ConceptView anchor) {
        return "\n[이번 턴에 내려온 자리]\n"
                + "학생이 물은 개념: " + notice.askedConceptName() + "\n"
                + "지금 설명할 개념: " + anchor.name() + "\n"
                + (notice.isJump()
                        ? "학생이 기초부터 다시 듣고 싶다고 해서, 물은 개념보다 훨씬 앞에서 배우는 개념까지 건너뛰어 왔다.\n"
                        : "학생이 더 쉬운 설명을 원해서, 물은 개념 바로 앞에 배우는 개념으로 한 칸 내려왔다.\n")
                + """
                학생이 물은 개념을 자료에서 못 찾은 것이 아니다. 일부러 더 쉬운 데로 내려온 것이다.
                그러니 "그 개념은 자료에 없다" 고 말하지 않는다. 학생이 물은 개념을 먼저 짚고,
                그것을 이해하려면 이 개념부터 알아야 한다는 흐름으로 설명한다.
                """;
    }

    /**
     * 개념 이름에 배우는 시기를 병기한다. 하향 탐색이 붙으면서 <b>한 답변 안에 여러 학년의 개념이
     * 섞이기</b> 때문에 필요해졌다 — 초등으로 건너뛴 답변에서 학생은 그게 언제 배운 것인지
     * 알 수 없고, 이름이 같은 개념이 중1과 초등에 걸쳐 11쌍 있다.
     *
     * <p><b>[이 단원의 개념 목록] 에는 붙이지 않는다.</b> 소단원이 달린 개념은 455개 중 중1
     * 210개뿐이고 초등 245개는 소단원이 없다. 그 목록에 병기하면 모든 줄이 {@code (중1)} 로
     * 끝나 정보가 아니라 잡음이 된다. 학년이 실제로 갈리는 곳은 앵커와 선수 개념이다.
     *
     * <p>학년 축은 {@code grade_band} 가 우선이다. 전처리로 중1에 편입된 4건이
     * {@code source_semester} 에 원천 학년(중3)을 그대로 갖고 있어, 중1이면 학기를 읽지 않는다.
     * task_20 §1-2 에서 정렬 축을 이렇게 정한 것과 같은 이유이며 같은 규칙이어야 한다.
     */
    private static String named(ConceptView concept) {
        String grade = gradeLabel(concept);
        return grade == null ? concept.name() : concept.name() + " — " + grade + " 때 배우는 개념";
    }

    /**
     * 학년을 <b>이미 풀어 쓴 문장</b>으로 돌려준다. 읽어 낼 수 없으면 {@code null} — 없는 학년을
     * 지어내느니 표시를 빼는 편이 낫다.
     *
     * <p><b>괄호를 쓰지 않는 것이 요점이다.</b> 예전에는 {@code 수직과 수선(초4)} 로 실어 보내고
     * 규칙 9 가 "괄호를 그대로 옮겨 쓰지 말고 문장으로 풀라"고 시켰는데, task_24 에서 39턴 중
     * 12~13턴이 {@code 등식과 좌변, 우변, 양변(중1)의 설명에 따르면 …} 처럼 그대로 내보냈다.
     * 모델에게 시키는 대신 <b>조립 단계에서 미리 풀면 옮겨 쓸 괄호가 애초에 없어진다.</b>
     */
    private static String gradeLabel(ConceptView concept) {
        if (concept.gradeBand() == GradeBand.MIDDLE_1) {
            return "중학교 1학년";
        }
        Matcher matcher = ELEMENTARY_GRADE.matcher(
                concept.sourceSemester() == null ? "" : concept.sourceSemester());
        return matcher.find() ? "초등학교 " + matcher.group(1) + "학년" : null;
    }
}
