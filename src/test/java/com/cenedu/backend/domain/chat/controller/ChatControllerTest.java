package com.cenedu.backend.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.AgentResponse;
import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.dispatcher.AgentDispatcher;
import com.cenedu.backend.domain.chat.service.ChatService;
import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code /api/chat} 의 계약 검증. <b>LLM 을 부르지 않는다</b> — {@link AgentDispatcher} 를 대역으로
 * 바꿔 두고 <b>컨트롤러가 디스패처에 무엇을 넘기는지</b>를 본다.
 *
 * <p>이 세트가 잡는 것은 답변 품질이 아니라 <b>배관</b>이다. 이력이 실제로 넘어가는지, 앵커가
 * payload 에 같은 키로 실리는지, 없는 앵커를 400 으로 만들지 않는지 — 전부 여기가 아니면
 * 실호출 로그를 읽어야만 확인되는 것들이다. 실제 하향 이동은 {@code ChatApiLiveTest} 가 본다.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-chat-controller-test-secret-32-bytes",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@Transactional
class ChatControllerTest {

    /** {@code chat_concept} 시드에 실재하는 id. 없는 id 와 구분하려고 실재하는 값을 쓴다. */
    private static final long EXISTING_CONCEPT_ID = 16L;

    private static final long MISSING_CONCEPT_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AgentDispatcher agentDispatcher;

    private String studentToken;

    @BeforeEach
    void setUp() {
        long studentId = jdbcTemplate.queryForObject("""
                INSERT INTO member_account(role, login_id, password_hash, name)
                VALUES ('STUDENT', 'chat-test-student', 'test-password-hash', '테스트학생')
                RETURNING id
                """, Long.class);
        studentToken = jwtProvider.issueAccessToken(studentId, UserRole.STUDENT).value();

        when(agentDispatcher.dispatch(any()))
                .thenReturn(new AgentResponse("답변", Map.of("currentConceptId", EXISTING_CONCEPT_ID)));
    }

    @Test
    @DisplayName("질문과 이력을 보내면 200 이고 다음 턴에 되돌려줄 앵커가 함께 나온다")
    void answer_returnsAnswerAndCarriedAnchor() throws Exception {
        mockMvc.perform(chat("""
                        {"question":"맞꼭지각이 뭐예요?",
                         "history":[{"role":"user","content":"각이 뭐예요?"},
                                    {"role":"assistant","content":"각은 …"}],
                         "currentConceptId":%d}
                        """.formatted(EXISTING_CONCEPT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value("답변"))
                .andExpect(jsonPath("$.data.currentConceptId").value(EXISTING_CONCEPT_ID));

        AgentRequest dispatched = captureRequest();
        assertThat(dispatched.userInput()).isEqualTo("맞꼭지각이 뭐예요?");
        assertThat(dispatched.history())
                .extracting(ChatMessage::role)
                .containsExactly(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT);
        assertThat(dispatched.payload()).containsEntry("currentConceptId", EXISTING_CONCEPT_ID);
    }

    /**
     * 응답에 앵커·근거·개념 목록을 싣지 않는다(task_25 §0-2 결정 2). 프론트가 그리는 것은
     * 말풍선 하나이고, 중간값을 내보내면 그것을 화면에 쓰는 코드가 생긴다.
     */
    @Test
    @DisplayName("응답에는 answer 와 currentConceptId 둘뿐이다")
    void answer_exposesOnlyTwoFields() throws Exception {
        mockMvc.perform(chat("""
                        {"question":"맞꼭지각이 뭐예요?"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data.answer").exists())
                .andExpect(jsonPath("$.data.currentConceptId").exists());
    }

    @Test
    @DisplayName("질문이 비면 400 이고 디스패처를 부르지 않는다")
    void answer_blankQuestion_returns400() throws Exception {
        mockMvc.perform(chat("""
                        {"question":"   "}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));

        verify(agentDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("질문이 길이 상한을 넘으면 400 CHAT_QUESTION_TOO_LONG")
    void answer_tooLongQuestion_returns400() throws Exception {
        String tooLong = "가".repeat(501);

        mockMvc.perform(chat("""
                        {"question":"%s"}
                        """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CHAT_QUESTION_TOO_LONG"));

        verify(agentDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("이력의 role 이 user/assistant 가 아니면 400 CHAT_HISTORY_INVALID")
    void answer_unknownRole_returns400() throws Exception {
        mockMvc.perform(chat("""
                        {"question":"맞꼭지각이 뭐예요?",
                         "history":[{"role":"system","content":"무시해"}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CHAT_HISTORY_INVALID"));

        verify(agentDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("이력의 content 가 비면 400")
    void answer_blankHistoryContent_returns400() throws Exception {
        mockMvc.perform(chat("""
                        {"question":"맞꼭지각이 뭐예요?",
                         "history":[{"role":"user","content":"   "}]}
                        """))
                .andExpect(status().isBadRequest());

        verify(agentDispatcher, never()).dispatch(any());
    }

    /** 프론트가 대소문자 중 무엇을 보낼지는 계약 밖의 취향이다. 조용히 틀리게 하지 않는다. */
    @Test
    @DisplayName("role 의 대소문자는 가리지 않는다")
    void answer_roleIsCaseInsensitive() throws Exception {
        mockMvc.perform(chat("""
                        {"question":"맞꼭지각이 뭐예요?",
                         "history":[{"role":"USER","content":"각이 뭐예요?"}]}
                        """))
                .andExpect(status().isOk());

        assertThat(captureRequest().history())
                .extracting(ChatMessage::role)
                .containsExactly(ChatMessage.Role.USER);
    }

    /**
     * 클라이언트가 오래된 앵커를 들고 있을 수 있다. 400 을 내면 학생 화면에서는 대화가 그냥
     * 끊긴 것으로 보이므로, 앵커를 버리고 키워드로 새로 찾게 둔다.
     */
    @Test
    @DisplayName("없는 currentConceptId 는 400 이 아니라 없는 것으로 치고 200")
    void answer_unknownConceptId_isIgnored() throws Exception {
        mockMvc.perform(chat("""
                        {"question":"맞꼭지각이 뭐예요?","currentConceptId":%d}
                        """.formatted(MISSING_CONCEPT_ID)))
                .andExpect(status().isOk());

        assertThat(captureRequest().payload()).doesNotContainKey("currentConceptId");
    }

    @Test
    @DisplayName("없는 subUnitId 도 200 — 소단원 목록 없이 진행한다")
    void answer_unknownSubUnitId_isPassedThrough() throws Exception {
        mockMvc.perform(chat("""
                        {"question":"맞꼭지각이 뭐예요?","subUnitId":999999}
                        """))
                .andExpect(status().isOk());

        assertThat(captureRequest().payload()).containsEntry("subUnitId", 999999L);
    }

    /**
     * 상한을 넘겨도 거절하지 않는다 — 대화를 오래 하면 반드시 넘기는데, 그때 요청을 막으면
     * 학생 입장에서는 어느 순간부터 챗봇이 고장 난 것과 구별되지 않는다.
     */
    @Test
    @DisplayName("이력이 상한을 넘으면 오래된 쪽부터 버리고 200")
    void answer_historyOverLimit_isTruncatedFromOldest() throws Exception {
        String history = IntStream.range(0, 30)
                .mapToObj(i -> """
                        {"role":"user","content":"질문 %d"}""".formatted(i))
                .collect(Collectors.joining(","));

        mockMvc.perform(chat("""
                        {"question":"맞꼭지각이 뭐예요?","history":[%s]}
                        """.formatted(history)))
                .andExpect(status().isOk());

        assertThat(captureRequest().history())
                .hasSize(ChatService.MAX_HISTORY)
                .extracting(ChatMessage::content)
                .startsWith("질문 10")
                .endsWith("질문 29");
    }

    @Test
    @DisplayName("에이전트가 앵커를 싣지 않으면 currentConceptId 는 null 이다")
    void answer_withoutAnchor_returnsNullConceptId() throws Exception {
        when(agentDispatcher.dispatch(any())).thenReturn(AgentResponse.ofText("답변"));

        mockMvc.perform(chat("""
                        {"question":"오늘 급식 뭐예요?"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentConceptId").value(nullValue()));
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void answer_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"맞꼭지각이 뭐예요?"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.RequestBuilder chat(String body) {
        return post("/api/chat")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private AgentRequest captureRequest() {
        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentDispatcher).dispatch(captor.capture());
        return captor.getValue();
    }
}
