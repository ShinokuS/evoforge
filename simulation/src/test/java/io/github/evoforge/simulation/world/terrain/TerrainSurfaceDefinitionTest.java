package io.github.evoforge.simulation.world.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.definition.NormalizedValue;
import org.junit.jupiter.api.Test;

final class TerrainSurfaceDefinitionTest {

    @Test
    void semanticControlsAreNormalizedAndStable() {
        TerrainSurfaceDefinition definition = TerrainSurfaceDefinition.of(0.2d, 0.4d, 0.6d, 0.8d);

        assertEquals(0.2d, definition.reliefIntensity().value());
        assertEquals(0.4d, definition.regionalRuggedness().value());
        assertEquals(0.6d, definition.plateauTendency().value());
        assertEquals(0.8d, definition.regionalReliefScale().value());
        assertEquals(TerrainSurfaceDefinition.of(0.68d, 0.55d, 0.35d, 0.50d), TerrainSurfaceDefinition.balanced());
    }

    @Test
    void nullControlsAreRejected() {
        NormalizedValue half = NormalizedValue.of(0.5d);

        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainSurfaceDefinition(null, half, half, half));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainSurfaceDefinition(half, null, half, half));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainSurfaceDefinition(half, half, null, half));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainSurfaceDefinition(half, half, half, null));
    }

    @Test
    void outOfRangeAuthoredValuesAreRejectedByNormalizedContract() {
        assertThrows(IllegalArgumentException.class, () -> TerrainSurfaceDefinition.of(-0.01d, 0.5d, 0.5d, 0.5d));
        assertThrows(IllegalArgumentException.class, () -> TerrainSurfaceDefinition.of(0.5d, 1.01d, 0.5d, 0.5d));
    }
}
