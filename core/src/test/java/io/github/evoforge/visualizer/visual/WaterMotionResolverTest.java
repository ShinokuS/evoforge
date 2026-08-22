package io.github.evoforge.visualizer.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.liquid.water.WaterFlowLookup;
import io.github.evoforge.simulation.world.liquid.water.WaterFlowSample;
import io.github.evoforge.simulation.world.liquid.water.WaterLookup;

final class WaterMotionResolverTest {

    private static final WaterLookup DEEP_WATER = (x, y, z) -> 80_000;

    @Test
    void missingActualTransferUsesCalmPresentation() {
        WaterMotionResolver resolver = new WaterMotionResolver(
                WaterFlowLookup.NONE,
                DEEP_WATER);

        assertEquals(WaterMotion.CALM, resolver.resolve(0, 0, 0));
    }

    @Test
    void significantHorizontalTransfersMapToCardinalPresentationMotion() {
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
        WaterMotionResolver resolver = new WaterMotionResolver(flow, DEEP_WATER);

        assertEquals(WaterMotion.EAST, resolver.resolve(0, 0, 0));
        assertEquals(WaterMotion.WEST, resolver.resolve(1, 0, 0));
        assertEquals(WaterMotion.NORTH, resolver.resolve(2, 0, 0));
        assertEquals(WaterMotion.SOUTH, resolver.resolve(3, 0, 0));
    }

    @Test
    void tinyEqualizationFluxInDeepWaterLooksCalm() {
        WaterFlowLookup flow = (x, y, z) -> new WaterFlowSample(1, 0, 0, 500);
        WaterMotionResolver resolver = new WaterMotionResolver(flow, DEEP_WATER);

        assertEquals(WaterMotion.CALM, resolver.resolve(0, 0, 0));
    }

    @Test
    void sameFluxRemainsVisibleWhenItIsLargeRelativeToShallowPuddle() {
        WaterFlowLookup flow = (x, y, z) -> new WaterFlowSample(1, 0, 0, 500);
        WaterLookup shallowWater = (x, y, z) -> 2_000;
        WaterMotionResolver resolver = new WaterMotionResolver(flow, shallowWater);

        assertEquals(WaterMotion.EAST, resolver.resolve(0, 0, 0));
    }

    @Test
    void actualDownwardTransferUsesFallingPresentationEvenAtLowRelativeFlux() {
        WaterFlowLookup flow = (x, y, z) -> x == 0 && y == 0 && z == 1
                ? new WaterFlowSample(0, 0, -1, 100)
                : null;
        WaterMotionResolver resolver = new WaterMotionResolver(flow, DEEP_WATER);

        assertEquals(WaterMotion.FALLING, resolver.resolve(0, 0, 1));
    }

    @Test
    void theoreticalSlopeWithoutSolverTransferRemainsCalm() {
        WaterFlowLookup flow = (x, y, z) -> null;
        WaterMotionResolver resolver = new WaterMotionResolver(flow, DEEP_WATER);

        assertEquals(WaterMotion.CALM, resolver.resolve(7, -4, 2));
    }
}
