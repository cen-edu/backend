package com.cenedu.backend.domain.problem.controller;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.annotations.Operation;

class ProblemEditControllerTest {
    @Test
    void edit_turn_endpoint는_문서화되어_있다() throws Exception {
        Method method = ProblemEditController.class.getDeclaredMethod("handleTurn", long.class,
                com.cenedu.backend.domain.problem.dto.request.ProblemEditTurnRequest.class,
                com.cenedu.backend.global.security.AuthenticatedUser.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponses.class)).isNotNull();
    }
}
