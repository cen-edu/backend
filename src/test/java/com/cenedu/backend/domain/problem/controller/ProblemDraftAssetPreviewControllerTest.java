package com.cenedu.backend.domain.problem.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.cenedu.backend.domain.problem.dto.response.DraftAssetPreviewResponse;
import com.cenedu.backend.domain.problem.service.ProblemDraftAssetPreviewService;
import com.cenedu.backend.global.security.AuthenticatedUser;
import com.cenedu.backend.global.common.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class ProblemDraftAssetPreviewControllerTest {
    @Test
    void 인증_회원_ID만_서비스에_전달하고_no_store_응답을_반환한다() {
        var service = mock(ProblemDraftAssetPreviewService.class);
        var preview = new DraftAssetPreviewResponse(2, 3, "F1", "image/svg+xml", 10, 10, "abc", "data:image/svg+xml;base64,PHN2Zy8+");
        when(service.preview(7, 2, 3, "F1")).thenReturn(preview);
        var controller = new ProblemDraftAssetPreviewController(service);

        var response = controller.preview(new AuthenticatedUser(7, UserRole.TEACHER), 2, 3, "F1");

        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getBody().data()).isEqualTo(preview);
        verify(service).preview(7, 2, 3, "F1");
    }
}
