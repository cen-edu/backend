package com.cenedu.backend.domain.analysis.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.cenedu.backend.domain.analysis.report.pdf.ClassReportPdfService;
import com.cenedu.backend.domain.analysis.report.pdf.StudentReportPdfService;
import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-analysis-pdf-controller-test-secret",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class AnalysisReportPdfControllerTest {

    private static final String CLASS_PATH =
            "/api/teacher/analysis/assignments/101/report.pdf";
    private static final String STUDENT_PATH =
            "/api/teacher/analysis/assignments/101/students/11/report.pdf";

    /** PDF 헤더. 응답 본문이 실제 PDF 인지 확인하는 최소 조건이다. */
    private static final byte[] PDF_BYTES = "%PDF-1.7 테스트".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ClassReportPdfService classReportPdfService;

    @MockitoBean
    private StudentReportPdfService studentReportPdfService;

    @Test
    @DisplayName("학급 PDF 를 첨부 파일로 내려준다")
    void downloadsClassReport() throws Exception {
        when(classReportPdfService.render(7L, 101L)).thenReturn(PDF_BYTES);
        when(classReportPdfService.fileName(101L))
                .thenReturn("class-analysis-report-101.pdf");

        mockMvc.perform(get(CLASS_PATH).header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"class-analysis-report-101.pdf\""));
    }

    @Test
    @DisplayName("학생 PDF 를 첨부 파일로 내려준다")
    void downloadsStudentReport() throws Exception {
        when(studentReportPdfService.render(7L, 101L, 11L)).thenReturn(PDF_BYTES);
        when(studentReportPdfService.fileName(101L, 11L))
                .thenReturn("analysis-report-101-11.pdf");

        mockMvc.perform(get(STUDENT_PATH).header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"analysis-report-101-11.pdf\""));
    }

    @Test
    @DisplayName("실패는 PDF 가 아니라 JSON 으로 돌아온다")
    void returnsJsonOnFailure() throws Exception {
        mockMvc.perform(get(CLASS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("학생 JWT 로 호출하면 403 을 반환한다")
    void rejectsStudentJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();

        mockMvc.perform(get(STUDENT_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private String teacherToken() {
        return jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
    }
}
