package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/**
 * Material-agnostic surface profile sampled at the four cardinal edges of one terrain cell.
 *
 * <p>Values are elevation subunits relative to the discrete surface-cell base. Generated target
 * patches may extend outside one cell for cliffs; shape templates normally remain within the cell.
 * No runtime Shape identity is encoded here.</p>
 */
public record TerrainSurfacePatch(
        long negativeXSubunits,
        long positiveXSubunits,
        long negativeYSubunits,
        long positiveYSubunits) {

    public static TerrainSurfacePatch flatTop() {
        long cell = ElevationField.SUBUNITS_PER_CELL;
        return new TerrainSurfacePatch(cell, cell, cell, cell);
    }

    /** Unit linear surface rising in one cardinal direction. */
    public static TerrainSurfacePatch cardinalRamp(int riseX, int riseY) {
        if (Math.abs(riseX) + Math.abs(riseY) != 1) {
            throw new IllegalArgumentException("surface rise must be one cardinal direction");
        }
        long half = ElevationField.SUBUNITS_PER_CELL / 2L;
        return new TerrainSurfacePatch(
                half - (long) riseX * half,
                half + (long) riseX * half,
                half - (long) riseY * half,
                half + (long) riseY * half);
    }

    public long gradientXSubunits() {
        return positiveXSubunits - negativeXSubunits;
    }

    public long gradientYSubunits() {
        return positiveYSubunits - negativeYSubunits;
    }

    public long reliefSubunits() {
        long minimum = Math.min(
                Math.min(negativeXSubunits, positiveXSubunits),
                Math.min(negativeYSubunits, positiveYSubunits));
        long maximum = Math.max(
                Math.max(negativeXSubunits, positiveXSubunits),
                Math.max(negativeYSubunits, positiveYSubunits));
        return maximum - minimum;
    }

    public long meanAbsoluteError(TerrainSurfacePatch other) {
        if (other == null) throw new IllegalArgumentException("other surface patch must not be null");
        long total = 0L;
        total = Math.addExact(total, absoluteDifference(negativeXSubunits, other.negativeXSubunits));
        total = Math.addExact(total, absoluteDifference(positiveXSubunits, other.positiveXSubunits));
        total = Math.addExact(total, absoluteDifference(negativeYSubunits, other.negativeYSubunits));
        total = Math.addExact(total, absoluteDifference(positiveYSubunits, other.positiveYSubunits));
        return total / 4L;
    }

    private static long absoluteDifference(long first, long second) {
        long difference = Math.subtractExact(first, second);
        if (difference == Long.MIN_VALUE) {
            throw new ArithmeticException("surface difference exceeds signed range");
        }
        return Math.abs(difference);
    }
}
