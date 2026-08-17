package io.github.evoforge.simulation.world.scale;

import io.github.evoforge.simulation.world.mechanics.measurement.PhysicalCellVolume;
import java.math.BigInteger;

/**
 * Physical dimensions represented by one discrete XYZ world cell.
 *
 * <p>XY cells are square and use {@code horizontalCellEdgeMillimeters}; Z may use an independent
 * physical height. Keeping the two dimensions explicit avoids silently assuming that rendered or
 * indexed XYZ cells are physically isotropic. All derived area/volume arithmetic is exact and uses
 * {@link BigInteger} so world scale does not overflow merely because a cell is large.</p>
 *
 * <p>This is world geometry/provenance, not runtime pacing. Tick duration belongs to the separate
 * simulation-time scale.</p>
 */
public record PhysicalSpaceScale(
        long horizontalCellEdgeMillimeters,
        long verticalCellHeightMillimeters) {

    private static final BigInteger CUBIC_MILLIMETERS_PER_MILLILITER = BigInteger.valueOf(1_000L);

    public PhysicalSpaceScale {
        if (horizontalCellEdgeMillimeters <= 0L) {
            throw new IllegalArgumentException("horizontal cell edge must be > 0 millimeters");
        }
        if (verticalCellHeightMillimeters <= 0L) {
            throw new IllegalArgumentException("vertical cell height must be > 0 millimeters");
        }
    }

    /** Convenience factory for physically cubic cells. */
    public static PhysicalSpaceScale cubicMillimeters(long cellEdgeMillimeters) {
        return new PhysicalSpaceScale(cellEdgeMillimeters, cellEdgeMillimeters);
    }

    /** Exact horizontal footprint of one XY column cell. */
    public BigInteger horizontalCellAreaSquareMillimeters() {
        BigInteger edge = BigInteger.valueOf(horizontalCellEdgeMillimeters);
        return edge.multiply(edge);
    }

    /** Exact physical volume of one completely open XYZ cell. */
    public BigInteger fullCellVolumeCubicMillimeters() {
        return horizontalCellAreaSquareMillimeters()
                .multiply(BigInteger.valueOf(verticalCellHeightMillimeters));
    }

    /**
     * Bridges the physical world scale into the current liquid-volume conversion contract.
     *
     * <p>{@link PhysicalCellVolume} currently stores whole millilitres. Scales whose exact cell
     * volume is fractional in millilitres remain valid physical scales, but cannot use this legacy
     * bridge until that converter gains rational physical-volume support.</p>
     */
    public PhysicalCellVolume physicalCellVolumeExact() {
        BigInteger[] division = fullCellVolumeCubicMillimeters()
                .divideAndRemainder(CUBIC_MILLIMETERS_PER_MILLILITER);
        if (division[1].signum() != 0) {
            throw new IllegalStateException(
                    "physical cell volume is not a whole milliliter: cubicMillimeters="
                            + fullCellVolumeCubicMillimeters());
        }
        return new PhysicalCellVolume(division[0].longValueExact());
    }
}
