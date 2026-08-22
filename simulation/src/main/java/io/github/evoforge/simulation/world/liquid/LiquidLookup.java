package io.github.evoforge.simulation.world.liquid;

import io.github.evoforge.simulation.world.space.measurement.CellVolume;

/** Read-only single-component free-liquid state at world cells. */
public interface LiquidLookup {

    /** Returns the resident liquid type, or {@code null} when the cell is dry. */
    LiquidTypeId typeAt(int x, int y, int z);

    /** Returns total free-liquid volume at the cell. Dry cells return zero. */
    int amount(int x, int y, int z);

    default int amountOf(
            LiquidTypeId type,
            int x,
            int y,
            int z) {

        if (type == null) {
            throw new IllegalArgumentException("liquid type must not be null");
        }
        LiquidTypeId resident = typeAt(x, y, z);
        return type.equals(resident)
                ? amount(x, y, z)
                : CellVolume.EMPTY;
    }
}
