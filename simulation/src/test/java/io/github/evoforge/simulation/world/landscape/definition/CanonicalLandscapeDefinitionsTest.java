package io.github.evoforge.simulation.world.landscape.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.calibration.soil.SoilDefinitionCompiler;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileBindings;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileResolver;
import io.github.evoforge.simulation.world.calibration.soil.SoilSemanticProfileBindings;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.mechanics.traversal.LandscapeTraversalDefinitionCompiler;
import io.github.evoforge.simulation.world.mechanics.traversal.LandscapeTraversalDefinitions;
import io.github.evoforge.simulation.world.mechanics.traversal.SurfaceTraversalCost;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;

final class CanonicalLandscapeDefinitionsTest {

    @Test
    void canonicalMaterialsKeepSemanticDefinitionsSeparateFromPhysicalResolution() {
        SoilDefinitionCompiler soil = new SoilDefinitionCompiler();
        LandscapeTraversalDefinitions traversal = new LandscapeTraversalDefinitions();

        DefinitionRegistry<LandscapeDefinitionId> definitions =
                new LandscapeDefinitionBootstrap(
                        new LandscapeTraversalDefinitionCompiler(traversal),
                        soil)
                        .load(canonicalLandscapeDirectory());
        SoilSemanticProfileBindings semantics = soil.bindings();
        SoilHydraulicProfileBindings hydraulics =
                SoilHydraulicProfileResolver.standard().resolve(semantics);

        assertTraversal(definitions, traversal, "core:topsoil", 1_050);
        assertTraversal(definitions, traversal, "core:soil", 1_100);
        assertTraversal(definitions, traversal, "core:sand", 1_300);

        SoilHydraulicProfile topsoil = hydraulics.require(TerrainMaterialKey.of("core:topsoil"));
        SoilHydraulicProfile subsoil = hydraulics.require(TerrainMaterialKey.of("core:soil"));
        SoilHydraulicProfile sand = hydraulics.require(TerrainMaterialKey.of("core:sand"));

        assertTrue(
                topsoil.fieldCapacityPartsPerMillion() > subsoil.fieldCapacityPartsPerMillion(),
                "higher authored organic-matter tendency should derive higher field capacity");
        assertTrue(
                compareRates(
                        sand.saturatedHydraulicConductivity(),
                        subsoil.saturatedHydraulicConductivity()) > 0,
                "coarser mineral character should derive higher saturated conductivity");

        assertNonSoil(definitions, semantics, hydraulics, traversal, "core:granite");
        assertNonSoil(definitions, semantics, hydraulics, traversal, "core:basalt");
        assertNonSoil(definitions, semantics, hydraulics, traversal, "core:limestone");
        assertNonSoil(definitions, semantics, hydraulics, traversal, "core:shale");

        assertTrue(definitions.isFrozen());
        assertTrue(traversal.isFrozen());
    }

    private static void assertTraversal(
            DefinitionRegistry<LandscapeDefinitionId> definitions,
            LandscapeTraversalDefinitions traversal,
            String key,
            long traversalCost) {
        LandscapeDefinitionId id = definitions.resolve(key);
        assertEquals(SurfaceTraversalCost.of(traversalCost), traversal.cost(id));
    }

    private static void assertNonSoil(
            DefinitionRegistry<LandscapeDefinitionId> definitions,
            SoilSemanticProfileBindings semantics,
            SoilHydraulicProfileBindings hydraulics,
            LandscapeTraversalDefinitions traversal,
            String key) {
        LandscapeDefinitionId id = definitions.resolve(key);
        assertEquals(SurfaceTraversalCost.neutral(), traversal.cost(id));
        TerrainMaterialKey materialKey = TerrainMaterialKey.of(key);
        assertNull(semantics.find(materialKey));
        assertNull(hydraulics.find(materialKey));
    }

    private static int compareRates(WaterDepthRate left, WaterDepthRate right) {
        return left.depthNanometersNumerator()
                .multiply(right.durationNanosecondsDenominator())
                .compareTo(right.depthNanometersNumerator()
                        .multiply(left.durationNanosecondsDenominator()));
    }

    private static Path canonicalLandscapeDirectory() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("assets/definitions/landscape");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("canonical landscape definition directory not found");
    }
}
