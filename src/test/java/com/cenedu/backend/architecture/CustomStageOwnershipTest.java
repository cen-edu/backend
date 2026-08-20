package com.cenedu.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 맞춤 단계 enum이 특정 도메인의 내부 타입으로 다시 들어가지 않도록 소유 경계를 검증한다. */
class CustomStageOwnershipTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.cenedu.backend");
    }

    @Test
    @DisplayName("CustomStage는 global common enum에 존재한다")
    void customStageIsOwnedByGlobalCommonEnums() {
        noClasses()
                .that().resideInAPackage("..domain.worksheet.entity.enums..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.cenedu.backend.global.common.enums.CustomStage")
                .check(classes);

        org.assertj.core.api.Assertions.assertThat(classes.get("com.cenedu.backend.global.common.enums.CustomStage"))
                .isNotNull();
    }

    @Test
    @DisplayName("CustomStage는 worksheet 전용 패키지에 남아 있지 않다")
    void worksheetPackageDoesNotOwnCustomStage() {
        org.assertj.core.api.Assertions.assertThat(classes.stream()
                        .anyMatch(javaClass -> javaClass.getFullName().equals(
                                "com.cenedu.backend.domain.worksheet.entity.enums.CustomStage")))
                .isFalse();
    }
}
