package com.cenedu.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AGENTS.md 3절 4번을 강제한다. 디스패처를 우회하는 경로가 생기면 여기서 빌드가 멈춘다.
 *
 * <p>규칙을 문서로만 두면 반드시 새는 지점이 생기고, 그 지점만 가드레일 없이 동작하게 된다.
 *
 * <p>{@code allowEmptyShould(true)} 를 붙인 이유: 아직 도메인 패키지가 비어 있다. 이게 없으면
 * ArchUnit 이 "검사할 클래스가 없다"며 실패해서, 정작 도메인 코드가 생기기도 전에 빌드가 막힌다.
 */
class AiClientAccessTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.cenedu.backend");
    }

    @Test
    @DisplayName("도메인 패키지는 LLM 클라이언트를 직접 호출하지 않는다 — AgentDispatcher 를 거쳐야 한다")
    void domainMustNotUseAiClientDirectly() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..ai.client..")
                .because("LLM 호출은 예외 없이 AgentDispatcher 를 거쳐야 가드레일이 한 곳에서 집행된다")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    @DisplayName("도메인 패키지는 Anthropic SDK 를 직접 참조하지 않는다")
    void domainMustNotUseAnthropicSdkDirectly() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("com.anthropic..")
                .because("도메인은 ai.client 래퍼조차 모르고 AgentDispatcher 만 알아야 한다")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    @DisplayName("에이전트 구현체는 도메인 컨트롤러를 참조하지 않는다 — 에이전트는 호출 가능한 서비스다")
    void agentsMustNotDependOnControllers() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..ai..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("에이전트는 HTTP 진입점이 아니라 인자를 받아 결과를 돌려주는 서비스다")
                .allowEmptyShould(true);

        rule.check(classes);
    }
}
