package com.cenedu.backend.ai.problem.adapter;

import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;

/** 문제 유형별 생성 규칙을 공통 시스템 프롬프트와 결합한다. */
@Component
public class ProblemGenerationPromptFactory {

    /** 서버가 기대하는 S1 JSON 계약과 생성 조건을 프롬프트로 만든다. */
    public ProblemGenerationPrompt create(ProblemGenerationCommand command) {
        var spec = command.specification();
        var curriculum = command.curriculum();
        String systemPrompt = """
                당신은 초중등 수학 문제 출제자다. 아래 조건으로 문제 하나를 생성하라.
                반드시 JSON 객체만 출력하고 Markdown 코드 펜스를 사용하지 마라.
                문제 유형·난이도·소단원은 CURRENT_REQUEST_JSON의 조건을 따른다.
                requestId, DB ID, schemaVersion, metadata, displayOrder, 논리 키는 출력하지 마라.
                contentBlocks의 첫 항목은 blockKind=TEXT, assetRef=null, markup=null로 작성한다.
                text에는 학생에게 실제로 보여줄 완결된 문제 문장을 넣는다.
                "발문", "문제", "문제 내용", "정답을 구하시오" 같은 자리표시자만 쓰면 안 된다.
                문제를 푸는 데 필요한 수치·조건·데이터는 모두 text 안에 직접 포함한다.
                포함하지 않은 그림·표·데이터를 "주어진", "다음", "아래"라고 참조하지 마라.
                문제를 출력하기 전에 반드시 다음 순서로 자체 검산하라:
                (1) 학생이 보는 contentBlocks[0].text만 읽고 풀이에 필요한 모든 정보를 확인한다.
                (2) 문제를 처음부터 직접 풀어 최종값을 계산한다.
                (3) answerUnits, choices의 정답, explanation의 결론에 같은 값을 대입해 일치 여부를 확인한다.
                어느 하나라도 계산 불가·정보 부족·값 불일치이면 그 문항을 출력하지 말고 조건을 만족하는 새 문항을 만든다.
                생성 과정의 검산 메모리나 숨은 전제는 JSON에 쓰지 말고, 검산된 결과만 출력한다.
                최상위 question은 contentBlocks[0].text와 같은 실제 문제 문장으로 작성한다.
                모든 최상위 목록(contentBlocks, choices, steps, answerUnits, rubricItems, assets)은
                사용하지 않더라도 반드시 []로 출력한다. 단일 객체로 출력하지 마라.
                explanation, learningGuide(conceptTitle, summary, keyPoints 1~3개)는 필수다.
                explanation에는 이 문제의 구체적인 계산 또는 모범 응답 방향을 담고 일반론만 쓰지 않는다.
                keyPoints는 직접적인 정답이나 계산 절차를 노출하지 않는다.
                학생에게 표시되는 contentBlocks, choices, steps, explanation, learningGuide의 수식은
                인라인 LaTeX인 $...$로 감싸라(예: $2^3$, $\\frac{1}{2}$). 일반 문장과 단위만 있는 텍스트는 감싸지 마라.
                answerUnits의 answerRaw는 화면 표시용 구분자($, $$, \\(, \\)) 없이 비교 가능한 원시값만 작성하라.
                현재 MVP에서는 그림 자산을 만들지 않으므로 assets는 항상 []다.
                유형별 규칙:
                %s
                """.formatted(typeRules(spec.questionType().name()));
        List<ChatMessage> messages = new java.util.ArrayList<>();
        if (command.references() != null && !command.references().isEmpty()) {
            messages.add(ChatMessage.user("FEW_SHOT_JSON\n" + new FewShotReferenceSerializer().serialize(curriculum, command.references())));
        }
        messages.add(ChatMessage.user("CURRENT_REQUEST_JSON\n" + currentRequest(command)));
        return new ProblemGenerationPrompt(systemPrompt, messages);
    }

    private String currentRequest(ProblemGenerationCommand command) {
        try {
            LinkedHashMap<String, Object> request = new LinkedHashMap<>();
            request.put("purpose", command.purpose().name()); request.put("specification", command.specification());
            request.put("curriculum", command.curriculum());
            if (command.personalizedEvidence() != null) {
                request.put("personalizedEvidence", command.personalizedEvidence());
            }
            request.put("instruction", "참고 문제의 구조와 전략만 참고하고 수치·문장·정답을 복사하지 마라.");
            return new ObjectMapper().writeValueAsString(request);
        } catch (Exception exception) { throw new IllegalStateException("현재 생성 요청 JSON을 만들 수 없습니다.", exception); }
    }

    private String typeRules(String type) {
        return switch (type) {
            case "MULTIPLE_CHOICE" -> """
                    choices는 최소 2개이며 각 항목은 {"content":"보기 내용"} 형식이다.
                    발문에는 실제 계산에 필요한 모든 값과 무엇을 구하는지 명확히 적는다.
                    보기 중 정확히 하나만 정답이 되게 검산한다. 오답 보기도 문제 조건과 모순되지 않는 수치로 만들되 정답과 중복하지 않는다.
                    JSON을 만들기 전에 문제를 직접 풀고,
                    최종 결과와 동일한 content를 가진 보기의 1부터 시작하는 순번 n을 찾아 answerRaw=Cn으로 적는다.
                    explanation의 최종 결론, 정답 보기 content, answerRaw가 반드시 서로 일치해야 한다.
                    steps와 rubricItems는 []다. answerUnits는 정확히 한 항목이며
                    {"stepIndex":null,"answerRaw":"C1","compareMethod":"CHOICE","diagnosticType":null,"displayUnit":null}
                    형식으로 실제 정답 보기의 1부터 시작하는 키(C1, C2 등)를 answerRaw에 넣는다.
                    """;
            case "SHORT_INPUT" -> """
                    choices, steps, rubricItems는 []다. answerUnits는 정확히 한 항목이며
                    {"stepIndex":null,"answerRaw":"정답","compareMethod":"VALUE","diagnosticType":null,"displayUnit":null}
                    형식이다. compareMethod는 VALUE, EXACT, SET, SUBST 중 하나다.
                    발문에는 answerRaw를 계산할 수 있는 실제 수치와 질문을 모두 포함한다.
                    정수·분수·소수 표현을 혼용하지 말고 answerRaw와 explanation의 최종 계산값을 문자 단위로 대조한다.
                    """;
            case "STEP_FILL" -> """
                    choices와 rubricItems는 []다. steps는 1~4개이며 각 항목은
                    {"label":"단계 이름","segments":[{"type":"TEXT","text":"식 또는 설명","answerUnitIndex":null},{"type":"BLANK","text":null,"answerUnitIndex":0}]}
                    형식이다. 각 단계에는 BLANK가 1~2개 있어야 한다. BLANK와 answerUnits는 전체 화면에 나오는 순서가 같아야 하며,
                    answerUnitIndex는 그 순서의 0부터 시작하는 인덱스다. TEXT의 answerUnitIndex는 반드시 null이다.
                    contentBlocks[0].text는 풀이 단계가 아닌 완결된 실제 문제여야 하고, steps는 그 문제의
                    해답 과정을 순서대로 나누어야 한다. 각 BLANK의 answerRaw를 단계 식에 대입해 전체 풀이가
                    성립하는지 검산하고, explanation의 계산 결과와도 일치시킨다.
                    answerUnits는 BLANK마다 하나씩 두고
                    {"stepIndex":0,"answerRaw":"정답","compareMethod":"VALUE","diagnosticType":"EXECUTE","displayUnit":null}
                    형식으로 작성한다. diagnosticType은 INTERPRET, MODEL, EXECUTE, ANSWER 중 하나다.
                    각 BLANK의 answerRaw를 실제 수치로 계산하고, 앞 단계의 답을 뒤 단계에서 참조할 때도 같은 값을 사용한다.
                    """;
            case "ESSAY" -> """
                    학생이 무엇을 설명하고 어떤 근거 또는 풀이 과정을 제시해야 하는지 명확한 발문을 만든다.
                    choices와 steps는 []다.
                    answerUnits는 정확히 [{"stepIndex":null,"answerRaw":null,"compareMethod":"RUBRIC","diagnosticType":null,"displayUnit":null}]다.
                    rubricItems는 2~5개이며 각 항목은
                    {"criterion":"채점 기준","weightPercent":정수} 형식이고 weightPercent 합계는 정확히 100이다.
                    """;
            default -> throw new IllegalArgumentException("지원하지 않는 문제 유형: " + type);
        };
    }
}
