package com.cenedu.backend;

import com.cenedu.backend.support.PostgresTestcontainer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 컨텍스트가 뜨는지 본다.
 *
 * <p>Flyway 를 켜고 {@code ddl-auto: validate} 로 고정한 뒤로는, 이 테스트가 마이그레이션과
 * 엔티티가 어긋나지 않는다는 것까지 함께 증명한다. 그래서 DB 가 필요하다.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
