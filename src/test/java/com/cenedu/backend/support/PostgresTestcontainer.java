package com.cenedu.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * DB 가 필요한 테스트가 함께 쓰는 PostgreSQL 컨테이너.
 *
 * <p>로컬에 띄워 둔 DB 에 붙지 않는다. 개발자마다 남아 있는 스키마가 달라서, 로컬 DB 를 쓰면
 * 마이그레이션이 실제로 통과하는지를 테스트가 증명하지 못한다. 매번 빈 컨테이너에서 시작한다.
 *
 * <p>이미지는 pgvector 판을 쓴다. infra.vector 가 벡터 컬럼을 쓰기 시작하면 순정 postgres
 * 이미지로는 마이그레이션이 실패한다.
 *
 * <p>{@code @Bean} 하나로 두면 스프링 테스트 컨텍스트 캐시가 컨테이너를 재사용한다. 테스트
 * 클래스마다 새로 띄우면 빌드가 그만큼 느려진다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg17")
                        .asCompatibleSubstituteFor("postgres"));
    }
}
