package io.github.evoforge.simulation.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

final class BytecodeArchitectureContractTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.evoforge.simulation");

    @Test
    void worldSemanticModulesAreAcyclic() {
        slices()
                .matching("io.github.evoforge.simulation.world.(*)..")
                .should()
                .beFreeOfCycles()
                .check(CLASSES);
    }

    @Test
    void worldSemanticsDoNotDependOnHigherLevelConsumers() {
        noClasses()
                .that()
                .resideInAPackage("io.github.evoforge.simulation.world..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "io.github.evoforge.simulation.mechanics..",
                        "io.github.evoforge.simulation.agents..")
                .check(CLASSES);
    }

    @Test
    void kernelRemainsDomainNeutral() {
        noClasses()
                .that()
                .resideInAPackage("io.github.evoforge.simulation.kernel..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "io.github.evoforge.simulation.world..",
                        "io.github.evoforge.simulation.mechanics..",
                        "io.github.evoforge.simulation.agents..",
                        "io.github.evoforge.simulation.runtime..",
                        "io.github.evoforge.simulation.genesis..")
                .check(CLASSES);
    }
}
