package io.github.evoforge.visualizer.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.water.WaterFlowLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterFlowSample;

final class WaterMotionResolverTest {

    @Test
    void missingActualTransferUsesCalmPresentation() {
        WaterMotionResolver resolver = new WaterMotionResolver(WaterFlowLookup.NONE);

        assertEquals(WaterMotion.CALM, resolver.resolve(0, 0, 0));
    }

    @Test
    void actualHorizontalTransfersMapToCardinalPresentationMotion() {
        WaterFlowLookup flow = (x, y, z) -> {
            if (z != 0) return null;
            return switch (x) {
                case 0 -> new WaterFlowSample(1, 0, 0, 10_000);
                case 1 -> new WaterFlowSample(-1, 0, 0, 10_000);
                case 2 -> new WaterFlowSample(0, 1, 0, 10_000);
                case 3 -> new WaterFlowSample(0, -1, 0, 10_000);
                default -> null;
            };
        };
        WaterMotionResolver resolver = new WaterMotionResolver(flow);

        assertEquals(WaterMotion.EAST, resolver.resolve(0, 0, 0));
        assertEquals(WaterMotion.WEST, resolver.resolve(1, 0, 0));
        assertEquals(WaterMotion.NORTH, resolver.resolve(2, 0, 0));
        assertEquals(WaterMotion.SOUTH, resolver.resolve(3, 0, 0));
    }

    @Test
    void actualDownwardTransferUsesFallingPresentation() {
        WaterFlowLookup flow = (x, y, z) -> x == 0 && y == 0 && z == 1
                ? new WaterFlowSample(0, 0, -1, 25_000)
                : null;
        WaterMotionResolver resolver = new WaterMotionResolver(flow);

        assertEquals(WaterMotion.FALLING, resolver.resolve(0, 0, 1));
    }

    @Test
    void theoreticalSlopeWithoutSolverTransferRemainsCalm() {
        WaterFlowLookup flow = (x, y, z) -> null;
        WaterMotionResolver resolver = new WaterMotionResolver(flow);

        assertEquals(WaterMotion.CALM, resolver.resolve(7, -4, 2));
    }
}
