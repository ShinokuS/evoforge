package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.world.landscape.soil.SoilMoistureSystem;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Deterministic local exchange from free Water into retained SoilMoisture.
 *
 * <p>The system inspects only Water cells already woken by authoritative Water
 * mutations. It therefore adds no world scan and naturally sleeps with the Water
 * frontier. A Water cell may share space with partial terrain; otherwise the
 * immediately lower terrain cell is treated as its supporting soil surface.
 */
public final class WaterSoilExchangeSystem {

    private final WaterSystem water;
    private final TerrainLookup terrain;
    private final SoilMoistureSystem soil;

    public WaterSoilExchangeSystem(
            WaterSystem water,
            TerrainLookup terrain,
            SoilMoistureSystem soil) {

        if (water == null || terrain == null || soil == null) {
            throw new IllegalArgumentException(
                    "water-soil exchange dependencies must not be null");
        }
        this.water = water;
        this.terrain = terrain;
        this.soil = soil;
    }

    /**
     * Infiltrates currently active free Water before the next flow solve and returns
     * the exact transferred volume.
     */
    public long update() {
        long infiltratedTotal = 0L;

        for (WaterCell cell : water.flowActivity().snapshotSorted()) {
            int amount = water.lookup().amount(
                    cell.x(),
                    cell.y(),
                    cell.z());
            if (amount <= CellVolume.EMPTY) {
                continue;
            }

            int terrainZ = supportingTerrainZ(cell);
            if (terrainZ == Integer.MIN_VALUE) {
                continue;
            }

            int infiltrated = soil.infiltrateAtMost(
                    cell.x(),
                    cell.y(),
                    terrainZ,
                    amount);
            if (infiltrated <= CellVolume.EMPTY) {
                continue;
            }

            int removed = water.removeAtMost(
                    cell.x(),
                    cell.y(),
                    cell.z(),
                    infiltrated);
            if (removed != infiltrated) {
                throw new IllegalStateException(
                        "Water changed during deterministic soil exchange at "
                                + cell
                                + ": infiltrated="
                                + infiltrated
                                + ", removed="
                                + removed);
            }
            infiltratedTotal = Math.addExact(
                    infiltratedTotal,
                    infiltrated);
        }

        return infiltratedTotal;
    }

    private int supportingTerrainZ(
            WaterCell cell) {

        if (terrain.contains(
                cell.x(),
                cell.y(),
                cell.z())) {
            return cell.z();
        }
        if (cell.z() == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        int below = cell.z() - 1;
        return terrain.contains(
                cell.x(),
                cell.y(),
                below)
                ? below
                : Integer.MIN_VALUE;
    }
}
