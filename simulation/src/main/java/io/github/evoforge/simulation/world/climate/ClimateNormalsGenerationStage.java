package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.genesis.ClimateNormalsSpec;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.HydroClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/**
 * First durable climate model.
 *
 * <p>V1-V4 did not author thermal climate, so they retain a uniform datum-temperature fallback.
 * V5 applies an explicit authored cooling rate to precise generated elevation. Hydrologic climate
 * rates remain exactly those requested by {@link HydroClimateSpec}; spatial precipitation and
 * evaporation mechanics are deliberately deferred to a later causal slice.</p>
 */
public final class ClimateNormalsGenerationStage implements ClimateNormalsGenerator {

    @Override
    public ClimateNormalsField generate(WorldGenesis genesis, ElevationField elevation) {
        if (genesis == null || elevation == null) {
            throw new IllegalArgumentException("climate generation inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        if (!bounds.equals(elevation.bounds())) {
            throw new IllegalArgumentException("elevation bounds must match climate genesis bounds");
        }

        GenerationRevision revision = genesis.generationRevision();
        boolean elevationAware;
        if (GenerationRevision.V1.equals(revision)
                || GenerationRevision.V2.equals(revision)
                || GenerationRevision.V3.equals(revision)
                || GenerationRevision.V4.equals(revision)) {
            elevationAware = false;
        } else if (GenerationRevision.V5.equals(revision)) {
            elevationAware = true;
        } else {
            throw new IllegalArgumentException(
                    "unsupported generation revision: " + revision.value());
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        int[] temperature = new int[area];
        CellVolumeRate[] precipitation = new CellVolumeRate[area];
        CellVolumeRate[] evaporation = new CellVolumeRate[area];

        ClimateNormalsSpec thermal = genesis.spec().climateNormals();
        HydroClimateSpec hydrologic = genesis.spec().hydroClimate();
        Arrays.fill(precipitation, hydrologic.precipitationSupply());
        Arrays.fill(evaporation, hydrologic.evaporativeDemand());

        int index = 0;
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                temperature[index++] = elevationAware
                        ? temperatureAt(thermal, elevation.elevationSubunitsAt(worldX, worldY))
                        : thermal.datumMeanTemperatureMilliCelsius();
            }
        }
        return new DenseClimateNormalsField(bounds, temperature, precipitation, evaporation);
    }

    private static int temperatureAt(ClimateNormalsSpec spec, long elevationSubunits) {
        long scaledCooling = Math.multiplyExact(
                elevationSubunits,
                (long) spec.coolingMilliCelsiusPerElevationCell());
        long cooling = Math.floorDiv(scaledCooling, ElevationField.SUBUNITS_PER_CELL);
        long temperature = Math.subtractExact(
                (long) spec.datumMeanTemperatureMilliCelsius(),
                cooling);
        int exact = Math.toIntExact(temperature);
        ClimateTemperature.ofMilliCelsius(exact);
        return exact;
    }
}
