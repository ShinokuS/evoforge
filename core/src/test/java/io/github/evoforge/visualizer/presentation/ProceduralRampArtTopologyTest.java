package io.github.evoforge.visualizer.presentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ProceduralRampArtTopologyTest {

    @Test
    void positiveGeometricSideMapsToCorrectRasterCrossSideForEveryDirection() {
        assertTrue(ProceduralRampArt.positiveSideIsLowCross(0));   // +Y: west
        assertFalse(ProceduralRampArt.positiveSideIsLowCross(1)); // +X: north
        assertTrue(ProceduralRampArt.positiveSideIsLowCross(2));   // -Y: east
        assertFalse(ProceduralRampArt.positiveSideIsLowCross(3));  // -X: south
    }
}
