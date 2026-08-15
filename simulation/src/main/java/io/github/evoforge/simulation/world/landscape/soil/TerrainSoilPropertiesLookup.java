package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Resolves porous Soil properties from terrain definition data plus deterministic
 * coordinate-local pore-capacity variation. Permeability remains material-owned.
 */
public final class TerrainSoilPropertiesLookup implements SoilPropertiesLookup {

    private static final long MIX_X = 0x9E3779B97F4A7C15L;
    private static final long MIX_Y = 0xBF58476D1CE4E5B9L;
    private static final long MIX_Z = 0x94D049BB133111EBL;
    private static final long MIX_DEFINITION = 0xD6E8FEB86659FD93L;

    private final TerrainLookup terrain;
    private final SoilPropertiesDefinitions definitions;
    private final SoilPropertiesVariationDefinitions variations;

    public TerrainSoilPropertiesLookup(
            TerrainLookup terrain,
            SoilPropertiesDefinitions definitions) {
        this(terrain, definitions, new SoilPropertiesVariationDefinitions());
    }

    public TerrainSoilPropertiesLookup(
            TerrainLookup terrain,
            SoilPropertiesDefinitions definitions,
            SoilPropertiesVariationDefinitions variations) {
        if (terrain == null || definitions == null || variations == null) {
            throw new IllegalArgumentException(
                    "soil property lookup dependencies must not be null");
        }
        this.terrain = terrain;
        this.definitions = definitions;
        this.variations = variations;
    }

    @Override
    public SoilProperties find(int x, int y, int z) {
        LandscapeDefinitionId id = terrain.find(x, y, z);
        if (id == null || !definitions.has(id)) return null;

        SoilProperties base = definitions.get(id);
        SoilPropertiesVariation variation = variations.find(id);
        if (variation == null || variation.capacityAmplitude() == CellVolume.EMPTY) {
            return base;
        }

        return new SoilProperties(
                variedCapacity(base.capacity(), variation, id, x, y, z),
                base.permeability());
    }

    private static int variedCapacity(
            int base,
            SoilPropertiesVariation variation,
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
        int offset = (int) Math.floorMod(mix64(mixed), span) - amplitude;
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
