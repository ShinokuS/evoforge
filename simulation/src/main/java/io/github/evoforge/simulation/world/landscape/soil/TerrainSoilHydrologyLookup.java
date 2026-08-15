package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Composes terrain material hydrology with deterministic coordinate-local capacity
 * variation. No runtime random state is consumed; identical seed and coordinates
 * always resolve to identical properties.
 */
public final class TerrainSoilHydrologyLookup
        implements SoilHydrologyLookup {

    private static final long MIX_X = 0x9E3779B97F4A7C15L;
    private static final long MIX_Y = 0xBF58476D1CE4E5B9L;
    private static final long MIX_Z = 0x94D049BB133111EBL;
    private static final long MIX_DEFINITION = 0xD6E8FEB86659FD93L;

    private final TerrainLookup terrain;
    private final SoilHydrologyDefinitions definitions;
    private final SoilHydrologyVariationDefinitions variations;

    public TerrainSoilHydrologyLookup(
            TerrainLookup terrain,
            SoilHydrologyDefinitions definitions) {
        this(
                terrain,
                definitions,
                new SoilHydrologyVariationDefinitions());
    }

    public TerrainSoilHydrologyLookup(
            TerrainLookup terrain,
            SoilHydrologyDefinitions definitions,
            SoilHydrologyVariationDefinitions variations) {

        if (terrain == null
                || definitions == null
                || variations == null) {
            throw new IllegalArgumentException(
                    "soil hydrology lookup dependencies must not be null");
        }
        this.terrain = terrain;
        this.definitions = definitions;
        this.variations = variations;
    }

    @Override
    public SoilHydrology find(
            int x,
            int y,
            int z) {

        LandscapeDefinitionId id = terrain.find(x, y, z);
        if (id == null || !definitions.has(id)) {
            return null;
        }

        SoilHydrology base = definitions.get(id);
        SoilHydrologyVariation variation = variations.find(id);
        if (variation == null
                || variation.capacityAmplitude() == CellVolume.EMPTY) {
            return base;
        }

        int capacity = variedCapacity(
                base.capacity(),
                variation,
                id,
                x,
                y,
                z);
        return new SoilHydrology(
                capacity,
                base.infiltrationLimit());
    }

    private static int variedCapacity(
            int base,
            SoilHydrologyVariation variation,
            LandscapeDefinitionId id,
            int x,
            int y,
            int z) {

        int amplitude = variation.capacityAmplitude();
        long span = ((long) amplitude * 2L) + 1L;
        long mixed = variation.seed()
                ^ (MIX_X * x)
                ^ (MIX_Y * y)
                ^ (MIX_Z * z)
                ^ (MIX_DEFINITION * id.asInt());
        long normalized = mix64(mixed);
        int offset = (int) Math.floorMod(normalized, span)
                - amplitude;

        long varied = (long) base + offset;
        return (int) Math.max(
                CellVolume.EMPTY,
                Math.min((long) CellVolume.FULL, varied));
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }
}
