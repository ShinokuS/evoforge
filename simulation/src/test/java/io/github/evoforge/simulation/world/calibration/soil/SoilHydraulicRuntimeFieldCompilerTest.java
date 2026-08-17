package io.github.evoforge.simulation.world.calibration.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesLookup;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class SoilHydraulicRuntimeFieldCompilerTest {
    private static final PhysicalSpaceScale ONE_METER_CELL =
            PhysicalSpaceScale.cubicMillimeters(1_000L);
    private static final SimulationTimeScale ONE_HOUR_TICK =
            SimulationTimeScale.of(Duration.ofHours(1));

    @Test
    void preservesLocalPhysicalDifferencesAndAuthoritativeNull() {
        WorldBounds bounds = new WorldBounds(-1, 1, 0, 0, 0, 0);
        ElevationField elevation = flat(bounds, 0);
        SoilHydraulicProfile fast = profile(450_000, 8L);
        SoilHydraulicProfile slow = profile(500_000, 1L);
        SoilHydraulicProfileField field = field(bounds, (x, y, z) ->
                x < 0 ? fast : x > 0 ? slow : null);

        SoilPropertiesLookup runtime = SoilHydraulicRuntimeFieldCompiler.compile(
                field, elevation, ONE_METER_CELL, ONE_HOUR_TICK);

        assertEquals(450_000, runtime.find(-1, 0, 0).capacity());
        assertEquals(8_000, runtime.find(-1, 0, 0).permeability());
        assertNull(runtime.find(0, 0, 0));
        assertEquals(500_000, runtime.find(1, 0, 0).capacity());
        assertEquals(1_000, runtime.find(1, 0, 0).permeability());
    }

    @Test
    void runtimeLookupDoesNotAskGeneratedFieldAboutOpenAir() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, 0, 1);
        SoilHydraulicProfile profile = profile(450_000, 8L);
        SoilHydraulicProfileField field = field(bounds, (x, y, z) -> {
            if (z > 0) throw new AssertionError("generated Soil field queried above terrain");
            return profile;
        });

        SoilPropertiesLookup runtime = SoilHydraulicRuntimeFieldCompiler.compile(
                field, flat(bounds, 0), ONE_METER_CELL, ONE_HOUR_TICK);

        assertNull(runtime.find(0, 0, 1));
        assertNull(runtime.find(1, 0, 0));
    }

    @Test
    void rejectsFieldWhoseBoundsDoNotMatchGeneratedTerrain() {
        ElevationField elevation = flat(new WorldBounds(0, 0, 0, 0, 0, 0), 0);
        SoilHydraulicProfileField field = field(
                new WorldBounds(0, 1, 0, 0, 0, 0),
                (x, y, z) -> null);

        assertThrows(
                IllegalArgumentException.class,
                () -> SoilHydraulicRuntimeFieldCompiler.compile(
                        field, elevation, ONE_METER_CELL, ONE_HOUR_TICK));
    }

    private static SoilHydraulicProfile profile(int porosity, long millimetersPerHour) {
        return new SoilHydraulicProfile(
                porosity,
                porosity / 2,
                porosity / 4,
                WaterDepthRate.ofMillimeters(millimetersPerHour, Duration.ofHours(1)));
    }

    private static ElevationField flat(WorldBounds bounds, int z) {
        return new ElevationField() {
            public WorldBounds bounds() { return bounds; }
            public int elevationAt(int x, int y) { return z; }
        };
    }

    private static SoilHydraulicProfileField field(
            WorldBounds bounds,
            SoilHydraulicProfileLookup lookup) {
        return new SoilHydraulicProfileField() {
            public WorldBounds bounds() { return bounds; }
            public SoilHydraulicProfile find(int x, int y, int z) {
                return lookup.find(x, y, z);
            }
        };
    }

    @FunctionalInterface
    private interface SoilHydraulicProfileLookup {
        SoilHydraulicProfile find(int x, int y, int z);
    }
}
