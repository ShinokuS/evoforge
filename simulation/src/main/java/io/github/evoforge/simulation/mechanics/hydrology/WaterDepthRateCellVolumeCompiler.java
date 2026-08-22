package io.github.evoforge.simulation.mechanics.hydrology;

import io.github.evoforge.simulation.kernel.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.space.measurement.CellVolumeRate;
import io.github.evoforge.simulation.world.space.measurement.PhysicalSpaceScale;
import java.math.BigInteger;
import io.github.evoforge.simulation.world.atmosphere.WaterDepthRate;

/**
 * Exact boundary conversion from a physical surface-water depth rate into normalized cell volume
 * per deterministic simulation tick.
 *
 * <p>The conversion uses the world's physical horizontal cell area, full-cell physical volume and
 * the runtime tick duration. No floating-point arithmetic or empirical balancing factor is used.</p>
 */
public final class WaterDepthRateCellVolumeCompiler {
    private static final BigInteger NANOMETERS_PER_MILLIMETER = BigInteger.valueOf(1_000_000L);
    private static final BigInteger CELL_VOLUME_FULL = BigInteger.valueOf(CellVolume.FULL);

    private WaterDepthRateCellVolumeCompiler() {
    }

    public static CellVolumeRate compile(
            WaterDepthRate depthRate,
            PhysicalSpaceScale spaceScale,
            SimulationTimeScale timeScale) {
        if (depthRate == null || spaceScale == null || timeScale == null) {
            throw new IllegalArgumentException("physical water-rate conversion inputs must not be null");
        }
        if (depthRate.depthNanometersNumerator().signum() == 0) {
            return CellVolumeRate.ZERO;
        }

        BigInteger numerator = depthRate.depthNanometersNumerator()
                .multiply(timeScale.nanosecondsPerTick())
                .multiply(spaceScale.horizontalCellAreaSquareMillimeters())
                .multiply(CELL_VOLUME_FULL);
        BigInteger denominator = depthRate.durationNanosecondsDenominator()
                .multiply(NANOMETERS_PER_MILLIMETER)
                .multiply(spaceScale.fullCellVolumeCubicMillimeters());

        BigInteger divisor = numerator.gcd(denominator);
        numerator = numerator.divide(divisor);
        denominator = denominator.divide(divisor);

        try {
            return CellVolumeRate.of(numerator.longValueExact(), denominator.longValueExact());
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "physical water-depth rate cannot be represented by current CellVolumeRate: "
                            + numerator + "/" + denominator,
                    overflow);
        }
    }
}
