package io.github.evoforge.visualizer.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

final class LandscapeTopologyTest {

    @Test
    void diagonalRequiresBothAdjacentCardinals() {
        int raw = LandscapeTopology.N
                | LandscapeTopology.NE
                | LandscapeTopology.E;

        assertEquals(raw, LandscapeTopology.normalize(raw));

        assertEquals(
                LandscapeTopology.N,
                LandscapeTopology.normalize(
                        LandscapeTopology.N | LandscapeTopology.NE));
        assertEquals(
                LandscapeTopology.E,
                LandscapeTopology.normalize(
                        LandscapeTopology.E | LandscapeTopology.NE));
    }

    @Test
    void allFourDiagonalRulesAreSymmetric() {
        int raw = LandscapeTopology.NE
                | LandscapeTopology.SE
                | LandscapeTopology.SW
                | LandscapeTopology.NW;

        assertEquals(0, LandscapeTopology.normalize(raw));
    }

    @Test
    void surfaceArtDoesNotCreatePointOnlyEarthCorners() {
        assertEquals(
                LandscapeTopology.N | LandscapeTopology.NE | LandscapeTopology.E,
                LandscapeTopology.normalizeSurfaceArt(
                        LandscapeTopology.N | LandscapeTopology.E));
        assertEquals(
                LandscapeTopology.E | LandscapeTopology.SE | LandscapeTopology.S,
                LandscapeTopology.normalizeSurfaceArt(
                        LandscapeTopology.E | LandscapeTopology.S));
        assertEquals(
                LandscapeTopology.S | LandscapeTopology.SW | LandscapeTopology.W,
                LandscapeTopology.normalizeSurfaceArt(
                        LandscapeTopology.S | LandscapeTopology.W));
        assertEquals(
                LandscapeTopology.W | LandscapeTopology.NW | LandscapeTopology.N,
                LandscapeTopology.normalizeSurfaceArt(
                        LandscapeTopology.W | LandscapeTopology.N));
    }

    @Test
    void worldCellVariantIsStableAndCoordinateSensitive() {
        int first = LandscapeTopology.variant(7, -3, 2, 4);
        int repeated = LandscapeTopology.variant(7, -3, 2, 4);

        assertEquals(first, repeated);

        boolean anyDifference = false;
        for (int x = 8; x < 20; x++) {
            if (LandscapeTopology.variant(x, -3, 2, 4) != first) {
                anyDifference = true;
                break;
            }
        }

        assertNotEquals(false, anyDifference);
    }
}
