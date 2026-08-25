package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Human-authored semantic controls for the Stage 6 continuous Terrain surface. */
public record TerrainSurfaceDefinition(
        NormalizedValue reliefIntensity,
        NormalizedValue regionalRuggedness,
        NormalizedValue plateauTendency,
        NormalizedValue regionalReliefScale) {

    public TerrainSurfaceDefinition {
        if (reliefIntensity == null
                || regionalRuggedness == null
                || plateauTendency == null
                || regionalReliefScale == null) {
            throw new IllegalArgumentException("terrain-surface controls must not be null");
        }
    }

    public static TerrainSurfaceDefinition of(
            double reliefIntensity,
            double regionalRuggedness,
            double plateauTendency,
            double regionalReliefScale) {
        return new TerrainSurfaceDefinition(
                NormalizedValue.of(reliefIntensity),
                NormalizedValue.of(regionalRuggedness),
                NormalizedValue.of(plateauTendency),
                NormalizedValue.of(regionalReliefScale));
    }

    /** Neutral inspection/default profile; arbitrary definitions remain valid. */
    public static TerrainSurfaceDefinition balanced() {
        return of(0.68d, 0.55d, 0.35d, 0.50d);
    }
}
