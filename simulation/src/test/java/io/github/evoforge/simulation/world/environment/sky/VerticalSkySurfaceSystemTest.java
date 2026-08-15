package io.github.evoforge.simulation.world.environment.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;

final class VerticalSkySurfaceSystemTest {

    @Test
    void resolvesWaterStrictlyAboveTerrainAndTerrainOnSharedAnchor() {
        Fixture fixture = new Fixture();
        fixture.landscape.placeTerrain(0, 0, 3, fixture.terrainId);
        fixture.landscape.setShape(0, 0, 3, RampShape.POSITIVE_X);

        assertEquals(
                new SkySurface(0, 0, 3, SkySurface.Kind.TERRAIN),
                fixture.sky.find(0, 0));

        fixture.water.addAtMost(0, 0, 3, 100_000);
        assertEquals(
                new SkySurface(0, 0, 3, SkySurface.Kind.TERRAIN),
                fixture.sky.find(0, 0));

        fixture.water.addAtMost(0, 0, 4, 100_000);
        assertEquals(
                new SkySurface(0, 0, 4, SkySurface.Kind.WATER),
                fixture.sky.find(0, 0));
    }

    @Test
    void resolvesWaterOnlyColumnsAndEmptyColumns() {
        Fixture fixture = new Fixture();
        fixture.water.addAtMost(-2, 7, 5, 40_000);

        assertEquals(
                new SkySurface(-2, 7, 5, SkySurface.Kind.WATER),
                fixture.sky.find(-2, 7));
        assertNull(fixture.sky.find(99, 99));
    }

    @Test
    void iteratesUnionOnceInStableXYOrder() {
        Fixture fixture = new Fixture();
        fixture.landscape.placeTerrain(5, 0, 0, fixture.terrainId);
        fixture.landscape.placeTerrain(0, 3, 2, fixture.terrainId);
        fixture.water.addAtMost(-4, 8, 1, 10_000);
        fixture.water.addAtMost(5, 0, 1, 20_000);

        List<String> surfaces = new ArrayList<>();
        fixture.sky.forEach(surface -> surfaces.add(
                surface.x()
                        + ":"
                        + surface.y()
                        + ":"
                        + surface.z()
                        + ":"
                        + surface.kind()));

        assertEquals(
                List.of(
                        "-4:8:1:WATER",
                        "0:3:2:TERRAIN",
                        "5:0:1:WATER"),
                surfaces);
    }

    private static final class Fixture {
        private final DefinitionRegistry<LandscapeDefinitionId> definitions =
                new DefinitionRegistry<>(
                        LandscapeDefinitionId::of,
                        LandscapeDefinitionId::asInt);
        private final LandscapeDefinitionId terrainId =
                definitions.register("test:terrain");
        private final LandscapeSystem landscape =
                LandscapeSystem.create(
                        new SparseTerrainStorage(),
                        definitions);
        private final LiquidSystem liquids = new LiquidSystem(
                new SparseLiquidStorage(),
                landscape.geometry());
        private final WaterSystem water = new WaterSystem(liquids);
        private final VerticalSkySurfaceSystem sky =
                new VerticalSkySurfaceSystem(
                        landscape.terrainSurfaces(),
                        water.surfaces());
    }
}
