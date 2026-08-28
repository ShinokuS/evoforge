package io.github.evoforge.visualizer.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.continuum.map.ContinuumScalarMapTileGenerator;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ContinuumMapInspectorModelTest {

    @Test
    void inspectorUsesAcceptedV15DefinitionAndDefaultSeed() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(256L, 256L);
        AtomicInteger builds = new AtomicInteger();
        try (ContinuumMapInspectorModel model = model(domain, builds)) {
            assertEquals(V15TerrainDefinition.balanced(), model.definition());
            assertEquals("balanced", model.profileName());
            assertEquals(ContinuumMapInspectorModel.DEFAULT_WORLD_SEED, model.seed());
            assertEquals(1, builds.get());
        }
    }

    @Test
    void customV15DefinitionRebuildsMapSourceWithoutMovingCameraOrChangingSeed() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(256L, 256L);
        AtomicInteger builds = new AtomicInteger();
        try (ContinuumMapInspectorModel model = model(domain, builds)) {
            model.panPixels(-73d, 41d);
            model.zoomAt(1.22d, 210d, 190d);
            double centerX = model.centerX();
            double centerY = model.centerY();
            double scale = model.pixelsPerWorldUnit();
            long seed = model.seed();
            V15TerrainDefinition custom = new V15TerrainDefinition(
                    NormalizedValue.of(0.72d),
                    NormalizedValue.of(0.61d),
                    NormalizedValue.of(0.39d),
                    NormalizedValue.of(0.68d),
                    NormalizedValue.of(0.52d),
                    NormalizedValue.of(0.57d),
                    NormalizedValue.of(0.43d));

            assertTrue(model.applyDefinition(custom));

            assertEquals(custom, model.definition());
            assertEquals("custom", model.profileName());
            assertEquals(centerX, model.centerX());
            assertEquals(centerY, model.centerY());
            assertEquals(scale, model.pixelsPerWorldUnit());
            assertEquals(seed, model.seed());
            assertEquals(2, builds.get());
            assertFalse(model.applyDefinition(custom));
            assertEquals(2, builds.get());
        }
    }

    @Test
    void changingSeedRebuildsExactTerrainWithoutMovingCameraOrChangingDefinition() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(256L, 256L);
        AtomicInteger builds = new AtomicInteger();
        try (ContinuumMapInspectorModel model = model(domain, builds)) {
            model.panPixels(57d, -34d);
            model.zoomAt(1.22d, 200d, 160d);
            double centerX = model.centerX();
            double centerY = model.centerY();
            double scale = model.pixelsPerWorldUnit();
            V15TerrainDefinition definition = model.definition();

            assertTrue(model.applySeed(42L));

            assertEquals(42L, model.seed());
            assertEquals(definition, model.definition());
            assertEquals(centerX, model.centerX());
            assertEquals(centerY, model.centerY());
            assertEquals(scale, model.pixelsPerWorldUnit());
            assertEquals(2, builds.get());
            assertFalse(model.applySeed(42L));
            assertEquals(2, builds.get());
        }
    }

    @Test
    void signedTerrainPresentationKeepsOceanAndLandOnOppositePaletteHalves() {
        long subunit = TerrainElevationField.SUBUNITS_PER_CELL;
        assertEquals(0d, ContinuumMapInspectorModel.normalizeTerrainElevation(-96L * subunit));
        assertEquals(1d, ContinuumMapInspectorModel.normalizeTerrainElevation(96L * subunit));
        assertEquals(127, quantized(ContinuumMapInspectorModel.normalizeTerrainElevation(-1L)));
        assertEquals(128, quantized(ContinuumMapInspectorModel.normalizeTerrainElevation(0L)));
    }

    private static int quantized(double value) {
        return (int) (value * 255d + 0.5d);
    }

    private static ContinuumMapInspectorModel model(
            ContinuumWorldDomain domain,
            AtomicInteger builds) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ContinuumMapInspectorModel.TerrainGeneratorFactory factory = (definition, seed) -> {
            builds.incrementAndGet();
            return new ContinuumScalarMapTileGenerator(domain, (x, y) -> 0.5d, 128);
        };
        return new ContinuumMapInspectorModel(
                domain,
                executor,
                640,
                480,
                V15TerrainDefinition.balanced(),
                ContinuumMapInspectorModel.DEFAULT_WORLD_SEED,
                factory);
    }
}
