package io.github.evoforge.simulation.world.calibration.rainfall;

import io.github.evoforge.simulation.world.climate.ClimateNormalsField;
import io.github.evoforge.simulation.world.climate.ClimateWaterNormal;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Default rainfall calibration that preserves generated physical precipitation normals exactly
 * while attaching independently supplied occurrence statistics.
 *
 * <p>No event cadence or runtime-driver parameter is guessed here. If occurrence statistics are not
 * known, callers must provide another semantic projection or another calibrator rather than relying
 * on hidden engine defaults.</p>
 */
public final class MeanPreservingRainfallRegimeCalibrator implements RainfallRegimeCalibrator {

    @Override
    public RainfallRegimeField calibrate(
            ClimateNormalsField climate,
            RainfallOccurrenceField occurrence) {
        if (climate == null || occurrence == null) {
            throw new IllegalArgumentException("rainfall calibration inputs must not be null");
        }
        if (!climate.bounds().equals(occurrence.bounds())) {
            throw new IllegalArgumentException("rainfall occurrence bounds must match climate bounds");
        }
        if (!ClimateWaterNormal.Kind.PHYSICAL_WATER_DEPTH_PER_TIME.equals(climate.waterNormalKind())) {
            throw new IllegalArgumentException(
                    "rainfall calibration requires physical water-depth climate normals");
        }

        WorldBounds bounds = climate.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        RainfallRegime[] regimes = new RainfallRegime[Math.multiplyExact(width, height)];

        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                int localX = worldX - bounds.minX();
                int localY = worldY - bounds.minY();
                int index = Math.addExact(Math.multiplyExact(localY, width), localX);
                regimes[index] = new RainfallRegime(
                        climate.precipitationDepthNormalAt(worldX, worldY),
                        occurrence.at(worldX, worldY));
            }
        }
        return new DenseField(bounds, width, regimes);
    }

    private record DenseField(
            WorldBounds bounds,
            int width,
            RainfallRegime[] regimes) implements RainfallRegimeField {

        private DenseField {
            regimes = regimes.clone();
        }

        @Override
        public RainfallRegime at(int x, int y) {
            if (!contains(x, y)) {
                throw new IllegalArgumentException(
                        "position outside rainfall regime field: (" + x + ", " + y + ")");
            }
            int localX = x - bounds.minX();
            int localY = y - bounds.minY();
            return regimes[Math.addExact(Math.multiplyExact(localY, width), localX)];
        }

        @Override
        public RainfallRegime[] regimes() {
            return regimes.clone();
        }
    }
}
