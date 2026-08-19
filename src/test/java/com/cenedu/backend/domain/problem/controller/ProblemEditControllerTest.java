package com.cenedu.backend.domain.problem.controller;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

class ProblemEditControllerTest {
    @Test
    void edit_turn_endpoint는_문서화되어_있다() throws Exception {
        Method method = ProblemEditController.class.getDeclaredMethod("handleTurn", long.class,
                com.cenedu.backend.domain.problem.dto.request.ProblemEditTurnRequest.class,
                com.cenedu.backend.global.security.AuthenticatedUser.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        ApiResponses responses = method.getAnnotation(ApiResponses.class);
        assertThat(responses).isNotNull();
        assertThat(responses.value()).extracting(r -> r.responseCode())
                .contains("200", "400", "409", "422");
        var success = responses.value()[0];
        var examples = success.content()[0].examples();
        assertThat(examples).extracting(ExampleObject::name)
                .contains("parametricPreview", "presentationalPatch", "structuralRegeneration",
                        "restore", "legacyFallback");
        assertThat(responses.value()[1].content()[0].examples()[0].name())
                .isEqualTo("rejectedCurriculum");
        assertThat(responses.value()[2].content()[0].examples()[0].name())
                .isEqualTo("staleBase");
        assertThat(responses.value()[3].content()[0].examples()[0].name())
                .isEqualTo("semanticValidationFailure");
    }
}
