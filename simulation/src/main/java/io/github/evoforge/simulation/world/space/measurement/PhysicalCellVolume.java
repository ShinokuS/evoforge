package io.github.evoforge.simulation.world.space.measurement;

import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import java.math.BigInteger;

/** Converts normalized cell-volume units to physical millilitres without redefining CellVolume. */
public record PhysicalCellVolume(long millilitersPerFullCell) {
    public PhysicalCellVolume {
        if (millilitersPerFullCell <= 0L) {
            throw new IllegalArgumentException("millilitersPerFullCell must be > 0");
        }
    }

    public int cellVolumeForMilliliters(long milliliters) {
        if (milliliters < 0L) throw new IllegalArgumentException("milliliters must be >= 0");
        if (milliliters == 0L) return CellVolume.EMPTY;
        BigInteger scaled = BigInteger.valueOf(milliliters)
                .multiply(BigInteger.valueOf(CellVolume.FULL));
        BigInteger units = scaled.divide(BigInteger.valueOf(millilitersPerFullCell));
        if (units.signum() == 0) return 1;
        if (units.compareTo(BigInteger.valueOf(CellVolume.FULL)) > 0) return CellVolume.FULL;
        return units.intValueExact();
    }

    public long millilitersForCellVolume(int amount) {
        int volume = CellVolume.requireValid(amount);
        BigInteger milliliters = BigInteger.valueOf(volume)
                .multiply(BigInteger.valueOf(millilitersPerFullCell))
                .divide(BigInteger.valueOf(CellVolume.FULL));
        return milliliters.longValueExact();
    }
}
