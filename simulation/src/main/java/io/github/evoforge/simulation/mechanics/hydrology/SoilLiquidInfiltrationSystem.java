package io.github.evoforge.simulation.mechanics.hydrology;

import io.github.evoforge.simulation.world.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.soil.SoilLiquidRetention;
import io.github.evoforge.simulation.world.soil.SoilLiquidSystem;

/** Deterministic transfer from active free liquid into retained Soil composition. */
public final class SoilLiquidInfiltrationSystem {

    private final LiquidSystem freeLiquids;
    private final TerrainLookup terrain;
    private final SoilLiquidRetention retention;

    public SoilLiquidInfiltrationSystem(
            LiquidSystem freeLiquids,
            TerrainLookup terrain,
            SoilLiquidSystem retainedLiquids) {
        this(
                freeLiquids,
                terrain,
                retainedLiquids == null ? null : retainedLiquids::infiltrateAtMost);
    }

    public SoilLiquidInfiltrationSystem(
            LiquidSystem freeLiquids,
            TerrainLookup terrain,
            SoilLiquidRetention retention) {
        if (freeLiquids == null || terrain == null || retention == null) {
            throw new IllegalArgumentException(
                    "Soil liquid infiltration dependencies must not be null");
        }
        this.freeLiquids = freeLiquids;
        this.terrain = terrain;
        this.retention = retention;
    }

    public long update() {
        long[] infiltratedTotal = {0L};
        freeLiquids.forEachActive((x, y, z) -> {
            LiquidTypeId type = freeLiquids.lookup().typeAt(x, y, z);
            if (type == null) return;
            int amount = freeLiquids.lookup().amount(x, y, z);
            if (amount <= CellVolume.EMPTY) return;

            int terrainZ = supportingTerrainZ(x, y, z);
            if (terrainZ == Integer.MIN_VALUE) return;

            int infiltrated = retention.infiltrateAtMost(
                    type, x, y, terrainZ, amount);
            if (infiltrated <= CellVolume.EMPTY) return;

            int removed = freeLiquids.removeAtMost(
                    type, x, y, z, infiltrated);
            if (removed != infiltrated) {
                throw new IllegalStateException(
                        "free liquid changed during deterministic Soil infiltration at ("
                                + x + ", " + y + ", " + z + "): type=" + type
                                + ", infiltrated=" + infiltrated
                                + ", removed=" + removed);
            }
            infiltratedTotal[0] = Math.addExact(infiltratedTotal[0], infiltrated);
        });
        return infiltratedTotal[0];
    }

    private int supportingTerrainZ(int x, int y, int z) {
        if (terrain.contains(x, y, z)) return z;
        if (z == Integer.MIN_VALUE) return Integer.MIN_VALUE;
        int below = z - 1;
        return terrain.contains(x, y, below) ? below : Integer.MIN_VALUE;
    }
}
