package com.cenedu.backend.ai.verification.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.problem.authoring.port.ProblemVerificationPort;
import com.cenedu.backend.support.PostgresTestcontainer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 검증 Adapter 의 빈들이 실제 컨텍스트에서 조립되는지 본다.
 *
 * <p><b>이 테스트가 없으면 배선 오류가 유닛 초록 뒤에 숨는다.</b> 선행 작업에서 좁은
 * {@code --tests} 필터 43개가 전부 통과했는데 앱이 기동하지 않았다 — 어댑터 테스트가
 * {@code ObjectMapper} 를 손으로 넘기고 누출 테스트가 컨텍스트를 띄우지 않아서, Jackson 패키지를
 * 착각해 빈이 없는 상태가 컴파일도 되고 유닛도 초록이었다. 빈 배선은 컨텍스트를 실제로 띄우는
 * 테스트만 본다.
 *
 * <p>Port 타입으로 받는 이유: 조율측이 보는 것이 {@code ProblemVerificationPort} 다. 구현 클래스로
 * 받으면 "빈은 있지만 Port 로 주입되지 않는" 경우를 놓친다.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class VerificationAdapterContextTest {

    @Autowired
    private ProblemVerificationPort verificationPort;

    @Autowired
    private ContentIntegrityChecker contentIntegrityChecker;

    @Autowired
    private FindingSanitizer findingSanitizer;

    @Autowired
    private ContentCheckProperties contentCheckProperties;

    @Test
    @DisplayName("Port 구현과 새 검사기·정제기가 빈으로 주입된다")
    void verificationBeansAreWired() {
        assertThat(verificationPort).isInstanceOf(ProblemVerificationAdapter.class);
        assertThat(contentIntegrityChecker).isNotNull();
        assertThat(findingSanitizer).isNotNull();
    }

    @Test
    @DisplayName("원본 검사 토글은 설정이 없어도 켜진다")
    void contentCheckIsEnabledByDefault() {
        // boolean 은 설정이 없으면 false 로 바인딩된다. @DefaultValue 가 빠지면 여기서 걸린다.
        assertThat(contentCheckProperties.enabled()).isTrue();
    }
}
