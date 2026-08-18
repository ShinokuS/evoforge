package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.atlas.WorldGenerationAlgorithms;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.preparation.GeneratedLandscapeProperties;
import io.github.evoforge.simulation.world.preparation.PreparedGeneratedWorld;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerationStage;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class GeneratedTerrainShapeBootstrapIntegrationTest {
    private static final WorldBounds BOUNDS = new WorldBounds(-1, 1, -1, 1, -3, 3);
    private static final TerrainMaterialKey GROUND = TerrainMaterialKey.of("test:shape-ground");

    @Test
    void preparedSurfaceShapeBecomesOrdinaryRuntimeGeometry() {
        ElevationField elevation = cardinalSlope();
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(BOUNDS), 42L);
        WorldAtlas atlas = new WorldAtlasGenerator(
                WorldGenerationAlgorithms.standard().withElevation(ignored -> elevation))
                .generate(genesis);
        TerrainShapeField shapes = TerrainShapeGenerationStage.standard().generate(elevation);
        assertNotNull(shapes.shapeOverrideAt(0, 0));

        TerrainMaterialField materials = new TerrainMaterialField() {
            @Override public WorldBounds bounds() { return BOUNDS; }
            @Override public TerrainMaterialKey materialAt(int x, int y, int z) { return GROUND; }
        };
        PreparedGeneratedWorld prepared = new PreparedGeneratedWorld(
                atlas,
                materials,
                shapes,
                GeneratedLandscapeProperties.empty(BOUNDS));

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(GROUND.value());
        GeneratedWorldRuntime world = new GeneratedWorldRuntimeBootstrap(
                AtmosphericRuntimePlans.disabled())
                .start(prepared, assembly, TerrainMaterialBindings.of(Map.of(GROUND, ground)));

        int surfaceZ = elevation.elevationAt(0, 0);
        assertSame(
                shapes.shapeOverrideAt(0, 0),
                world.runtime().view().geometry().find(0, 0, surfaceZ));
    }

    private static ElevationField cardinalSlope() {
        return new ElevationField() {
            @Override public WorldBounds bounds() { return BOUNDS; }

            @Override
            public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(
                        elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                if (!contains(x, y)) throw new IllegalArgumentException("outside test elevation");
                return (long) x * SUBUNITS_PER_CELL + SUBUNITS_PER_CELL / 2L;
            }
        };
    }
}
