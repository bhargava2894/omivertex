package com.softility.omivertex;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Executable enforcement of the conventions in AGENTS.md. These run with the normal
 * test suite, so any agent (or human) that breaks a structural rule fails the build —
 * no memory or code review required. Add a rule here whenever a convention can be
 * mechanically checked.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.softility.omivertex");
    }

    @Test
    void domainEntitiesArePure() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..web..", "..service..", "..repository..")
                .because("domain entities must not know about the layers above them")
                .check(classes);
    }

    @Test
    void nothingDependsOnControllers() {
        // DTOs live in web.dto and are legitimately built by services, so we forbid
        // depending on the controllers themselves (by annotation), not the whole web package.
        noClasses().that().resideInAnyPackage("..service..", "..repository..", "..domain..")
                .should().dependOnClassesThat().areAnnotatedWith(RestController.class)
                .because("the web layer is the top of the stack; lower layers must not reach up to controllers")
                .check(classes);
    }

    @Test
    void statusAndRoleFieldsAreEnumsNotStrings() {
        fields().that().haveName("status").or().haveName("role")
                .and().areDeclaredInClassesThat().resideInAPackage("..domain..")
                .should().notHaveRawType(String.class)
                .because("fixed-value fields must be enums, never String (see the AppUser drift)")
                .check(classes);
    }

    @Test
    void controllersDoNotReturnDomainEntities() {
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().arePublic()
                .should().notHaveRawReturnType(resideInDomain())
                .because("the web boundary speaks DTOs, never JPA entities")
                .check(classes);
    }

    @Test
    void controllersAreNamedAndPlacedConsistently() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .andShould().resideInAPackage("..web..")
                .check(classes);
    }

    @Test
    void servicesResideInServicePackage() {
        classes().that().areAnnotatedWith(org.springframework.stereotype.Service.class)
                .should().resideInAPackage("..service..")
                .check(classes);
    }

    private static com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass> resideInDomain() {
        return com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("..domain..");
    }
}
