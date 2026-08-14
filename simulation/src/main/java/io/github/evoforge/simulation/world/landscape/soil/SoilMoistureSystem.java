package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Authoritative finite moisture retained by terrain cells. */
public final class SoilMoistureSystem {

    private final SoilMoistureStorage storage;
    private final TerrainLookup terrain;
    private final SoilHydrologyDefinitions hydrology;
    private final SoilMoistureLookup lookup;

    public SoilMoistureSystem(
            SoilMoistureStorage storage,
            TerrainLookup terrain,
            SoilHydrologyDefinitions hydrology) {

        if (storage == null) {
            throw new IllegalArgumentException(
                    "storage must not be null");
        }
        if (terrain == null) {
            throw new IllegalArgumentException(
                    "terrain must not be null");
        }
        if (hydrology == null) {
            throw new IllegalArgumentException(
                    "hydrology must not be null");
        }

        this.storage = storage;
        this.terrain = terrain;
        this.hydrology = hydrology;
        lookup = storage::amount;
    }

    public SoilMoistureLookup lookup() {
        return lookup;
    }

    /**
     * Infiltrates precipitation for one simulation tick, bounded by both the
     * landscape material's infiltration rate and remaining moisture capacity.
     * Missing soil hydrology means that the terrain does not absorb water.
     */
    public int infiltrateAtMost(
            int x,
            int y,
            int z,
            int requested) {

        requireNonNegative(requested);
        if (requested == CellVolume.EMPTY) {
            return CellVolume.EMPTY;
        }

        LandscapeDefinitionId definitionId = terrain.find(x, y, z);
        if (definitionId == null
                || !hydrology.has(definitionId)) {
            return CellVolume.EMPTY;
        }

        SoilHydrology definition = hydrology.get(definitionId);
        int current = currentAmount(x, y, z);
        int available = Math.max(
                CellVolume.EMPTY,
                definition.capacity() - current);
        int infiltrated = Math.min(
                requested,
                Math.min(
                        definition.infiltrationPerTick(),
                        available));

        if (infiltrated == CellVolume.EMPTY) {
            return CellVolume.EMPTY;
        }

        storage.put(
                x,
                y,
                z,
                current + infiltrated);
        return infiltrated;
    }

    public int removeAtMost(
            int x,
            int y,
            int z,
            int requested) {

        requireNonNegative(requested);
        if (requested == CellVolume.EMPTY) {
            return CellVolume.EMPTY;
        }

        int current = currentAmount(x, y, z);
        int removed = Math.min(requested, current);
        if (removed == CellVolume.EMPTY) {
            return CellVolume.EMPTY;
        }

        int remaining = current - removed;
        if (remaining == CellVolume.EMPTY) {
            storage.remove(x, y, z);
        } else {
            storage.put(x, y, z, remaining);
        }
        return removed;
    }

    private int currentAmount(
            int x,
            int y,
            int z) {

        return CellVolume.requireValid(
                storage.amount(x, y, z));
    }

    private static void requireNonNegative(
            int requested) {

        if (requested < CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "requested soil moisture volume must not be negative: "
                            + requested);
        }
    }
}
