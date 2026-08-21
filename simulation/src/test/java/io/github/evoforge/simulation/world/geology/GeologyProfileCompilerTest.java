package io.github.evoforge.simulation.world.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GeologyProfileCompilerTest {

    @Test
    void canonicalAssetMatchesCoreCompositionAndUsesStableSortedOrder() {
        CompiledGeologyProfile assetProfile = new GeologyProfileCompiler().compile(
                new GeologyProfileLoader().load(asset(
                        "assets/definitions/geology/temperate-crust.json")));
        CompiledGeologyProfile coreProfile = GeologyProfiles.temperateCrust();

        assertEquals(GeologyProfiles.TEMPERATE_CRUST, assetProfile.key());
        assertEquals(coreProfile.units(), assetProfile.units());
        assertEquals(coreProfile.materials(), assetProfile.materials());
        assertEquals(List.of(
                GeologyProfiles.BASALT,
                GeologyProfiles.GRANITE,
                GeologyProfiles.LIMESTONE,
                GeologyProfiles.SHALE), assetProfile.units());
    }

    @Test
    void authoredUnitOrderDoesNotChangeCompiledGenerationOrder() {
        GeologyProfileDefinition original = new GeologyProfileLoader().load(asset(
                "assets/definitions/geology/temperate-crust.json"));
        List<GeologyProfileDefinition.UnitDefinition> reversed = new ArrayList<>(original.units());
        Collections.reverse(reversed);

        CompiledGeologyProfile first = new GeologyProfileCompiler().compile(original);
        CompiledGeologyProfile second = new GeologyProfileCompiler().compile(
                new GeologyProfileDefinition(original.key(), reversed));

        assertEquals(first.units(), second.units());
        assertEquals(first.materials(), second.materials());
    }

    @Test
    void strictLoaderRejectsUnknownFields() {
        assertThrows(IllegalArgumentException.class, () -> new GeologyProfileLoader().parse(
                """
                {
                  "key": "test:geology",
                  "units": [{"key":"test:rock","material":"test:rock"}],
                  "frequency": 0.5
                }
                """,
                "test"));
    }

    @Test
    void compilerRejectsDuplicateUnitKeys() {
        GeologyUnitKey key = GeologyUnitKey.of("test:rock");
        GeologyProfileDefinition definition = new GeologyProfileDefinition(
                "test:geology",
                List.of(
                        new GeologyProfileDefinition.UnitDefinition(
                                key, GeologyMaterialKey.of("test:first")),
                        new GeologyProfileDefinition.UnitDefinition(
                                key, GeologyMaterialKey.of("test:second"))));

        assertThrows(
                IllegalArgumentException.class,
                () -> new GeologyProfileCompiler().compile(definition));
    }

    private static Path asset(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("canonical geology asset not found: " + relative);
    }
}
