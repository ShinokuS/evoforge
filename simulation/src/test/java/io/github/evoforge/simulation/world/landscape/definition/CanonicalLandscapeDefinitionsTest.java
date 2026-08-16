package io.github.evoforge.simulation.world.landscape.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesDefinitionCompiler;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesDefinitions;
import io.github.evoforge.simulation.world.mechanics.traversal.LandscapeTraversalDefinitionCompiler;
import io.github.evoforge.simulation.world.mechanics.traversal.LandscapeTraversalDefinitions;
import io.github.evoforge.simulation.world.mechanics.traversal.SurfaceTraversalCost;

final class CanonicalLandscapeDefinitionsTest {

    @Test
    void canonicalMaterialsLoadThroughGenericDefinitionPipeline() {
        SoilPropertiesDefinitions soil = new SoilPropertiesDefinitions();
        LandscapeTraversalDefinitions traversal =
                new LandscapeTraversalDefinitions();

        DefinitionRegistry<LandscapeDefinitionId> definitions =
                new LandscapeDefinitionBootstrap(
                        new LandscapeTraversalDefinitionCompiler(traversal),
                        new SoilPropertiesDefinitionCompiler(soil))
                        .load(canonicalLandscapeDirectory());

        assertMaterial(
                definitions,
                soil,
                traversal,
                "core:topsoil",
                1_050,
                new SoilProperties(550_000, 100_000));
        assertMaterial(
                definitions,
                soil,
                traversal,
                "core:soil",
                1_100,
                new SoilProperties(450_000, 60_000));
        assertMaterial(
                definitions,
                soil,
                traversal,
                "core:sand",
                1_300,
                new SoilProperties(350_000, 250_000));

        LandscapeDefinitionId granite = definitions.resolve("core:granite");
        assertEquals(SurfaceTraversalCost.neutral(), traversal.cost(granite));
        assertFalse(soil.has(granite));

        assertTrue(definitions.isFrozen());
        assertTrue(soil.isFrozen());
        assertTrue(traversal.isFrozen());
    }

    private static void assertMaterial(
            DefinitionRegistry<LandscapeDefinitionId> definitions,
            SoilPropertiesDefinitions soil,
            LandscapeTraversalDefinitions traversal,
            String key,
            long traversalCost,
            SoilProperties expectedSoil) {

        LandscapeDefinitionId id = definitions.resolve(key);

        assertEquals(
                SurfaceTraversalCost.of(traversalCost),
                traversal.cost(id));
        assertEquals(expectedSoil, soil.get(id));
    }

    private static Path canonicalLandscapeDirectory() {
        Path current = Path.of("").toAbsolutePath();

        while (current != null) {
            Path candidate = current.resolve(
                    "assets/definitions/landscape");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        throw new IllegalStateException(
                "canonical landscape definition directory not found");
    }
}
