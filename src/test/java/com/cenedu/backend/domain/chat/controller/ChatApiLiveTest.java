package com.cenedu.backend.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP 경로에서 하향 이동과 앵커 왕복이 실제로 도는지 본다. <b>LLM 을 부른다.</b>
 *
 * <p><b>이 파일이 task_25 의 성공 판정 축이다.</b> 지금까지 하향 이동은 전부 러너 안에서만
 * 확인됐고, 러너는 엔진을 직접 부르며 payload 도 자기가 만든다. 컨트롤러·서비스·디스패처를
 * 지나는 경로에서 같은 일이 일어나는지는 아무도 본 적이 없다 — payload 키 이름이 한 글자만
 * 달라도 되감김으로 조용히 돌아가는데, 그 실패는 200 응답과 구별되지 않는다.
 *
 * <p>기대 앵커는 task_20 1순위 전수표에서 읽었다: {@code 이항}(88)의 1칸 아래는
 * {@code 등식과 좌변, 우변, 양변}(84)이다.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-chat-live-test-secret-32-bytes-long",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@Transactional
class ChatApiLiveTest {

    private static final Path REPORT = Path.of("build", "measurements", "25_chat_api.txt");

    /** {@code 이항}. 이 세트가 출발점으로 쓰는 개념이다. */
    private static final long IHANG = 88L;

    /** {@code 이항} 의 1순위 선수 {@code 등식과 좌변, 우변, 양변}. 한 칸 내려가면 여기다. */
    private static final long ONE_STEP_DOWN = 84L;

    private static final StringBuilder REPORT_LINES = new StringBuilder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String studentToken;

    @BeforeEach
    void setUp() {
        long studentId = jdbcTemplate.queryForObject("""
                INSERT INTO member_account(role, login_id, password_hash, name)
                VALUES ('STUDENT', 'chat-live-student', 'test-password-hash', '테스트학생')
                RETURNING id
                """, Long.class);
        studentToken = jwtProvider.issueAccessToken(studentId, UserRole.STUDENT).value();
    }

    /**
     * 시나리오 1 — 2턴 하향. <b>이력과 앵커를 규약대로 되돌려주면 앵커가 내려가야 한다.</b>
     */
    @Test
    @DisplayName("이력과 앵커를 되돌려주면 2턴째에 앵커가 한 칸 내려간다")
    void twoTurnDescent() throws Exception {
        Turn first = ask("""
                {"question":"이항이 뭐예요?"}
                """);
        record("1턴 [이항이 뭐예요?] answer=%d자 conceptId=%s".formatted(first.answerLength(), first.conceptId()));

        Turn second = ask("""
                {"question":"이해가 안 돼요",
                 "history":[{"role":"user","content":"이항이 뭐예요?"},
                            {"role":"assistant","content":%s}],
                 "currentConceptId":%d}
                """.formatted(json(first.answer()), first.conceptId()));
        record("2턴 [이해가 안 돼요] answer=%d자 conceptId=%s (되돌려준 앵커 %s)"
                .formatted(second.answerLength(), second.conceptId(), first.conceptId()));

        assertThat(first.conceptId()).isNotNull();
        assertThat(second.conceptId())
                .as("2턴째 앵커가 1턴째와 달라야 한다 — 같으면 이동이 일어나지 않은 것이다")
                .isNotNull()
                .isNotEqualTo(first.conceptId());
    }

    /**
     * 시나리오 2 — 이력 없이 하향 신호. <b>첫 발화 가드가 걸려 이동하면 안 된다</b>(task_24c §0-1).
     *
     * <p>"이동하지 않았다" 를 HTTP 응답만으로 보려면 <b>내려갔을 자리에 있지 않은지</b>를 본다.
     * 가드가 없으면 {@code 이항}(88)에서 1순위 선수 {@code 84} 로 내려간다.
     */
    @Test
    @DisplayName("이력 없이 하향 신호를 보내면 가드가 걸려 이동하지 않는다")
    void firstUtteranceDoesNotDescend() throws Exception {
        Turn turn = ask("""
                {"question":"이항이 뭔지 하나도 모르겠어요"}
                """);
        record("가드 [이항이 뭔지 하나도 모르겠어요, 이력 없음] conceptId=%s (내려갔다면 %d)"
                .formatted(turn.conceptId(), ONE_STEP_DOWN));

        assertThat(turn.conceptId())
                .as("첫 발화인데 한 칸 내려간 자리에 앵커링됐다면 가드가 걸리지 않은 것이다")
                .isNotEqualTo(ONE_STEP_DOWN);
    }

    /**
     * 시나리오 3 — <b>판정이 아니라 관측이다.</b> 프론트가 {@code currentConceptId} 를 돌려주지
     * 않을 때 무슨 일이 생기는지를 문서에 남기기 위한 것이며, 결과가 어떻든 고치지 않는다.
     */
    @Test
    @DisplayName("앵커를 되돌려주지 않으면 무슨 일이 생기는지 기록한다")
    void observeMissingAnchorRoundTrip() throws Exception {
        Turn first = ask("""
                {"question":"이항이 뭐예요?"}
                """);

        Turn second = ask("""
                {"question":"이해가 안 돼요",
                 "history":[{"role":"user","content":"이항이 뭐예요?"},
                            {"role":"assistant","content":%s}]}
                """.formatted(json(first.answer())));

        record("앵커 누락 관측 — 1턴 conceptId=%s → 2턴 conceptId=%s (이력만 보냄)"
                .formatted(first.conceptId(), second.conceptId()));
        record("  참고: 앵커를 되돌려줬다면 %d 에서 한 칸 내려가 %d 가 기대값이다"
                .formatted(IHANG, ONE_STEP_DOWN));

        assertThat(second.answerLength()).isPositive();
        flush();
    }

    private Turn ask(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        return new Turn(JsonPath.read(json, "$.data.answer"),
                readConceptId(json));
    }

    private static Long readConceptId(String json) {
        Number value = JsonPath.read(json, "$.data.currentConceptId");
        return value == null ? null : value.longValue();
    }

    /** 답변 원문을 JSON 문자열로 안전하게 싣는다 — 줄바꿈과 큰따옴표가 그대로 들어간다. */
    private static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }

    private static void record(String line) {
        REPORT_LINES.append(line).append('\n');
        System.out.println("[측정] " + line);
    }

    private static void flush() throws IOException {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, REPORT_LINES.toString(), StandardCharsets.UTF_8);
    }

    private record Turn(String answer, Long conceptId) {

        int answerLength() {
            return answer == null ? 0 : answer.length();
        }
    }
}
