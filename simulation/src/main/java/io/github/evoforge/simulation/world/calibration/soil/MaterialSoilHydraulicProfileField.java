package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;

/**
 * Baseline generated Soil field that projects stable material identities onto prepared hydraulics.
 *
 * <p>This adapter is intentionally deterministic and contains no coordinate noise. A later geology,
 * deposition or pedogenesis model can replace it while preserving the same field contract.</p>
 */
public final class MaterialSoilHydraulicProfileField implements SoilHydraulicProfileField {
    private final TerrainMaterialField materials;
    private final SoilHydraulicProfileBindings profiles;

    public MaterialSoilHydraulicProfileField(
            TerrainMaterialField materials,
            SoilHydraulicProfileBindings profiles) {
        if (materials == null || profiles == null) {
            throw new IllegalArgumentException("generated Soil field inputs must not be null");
        }
        this.materials = materials;
        this.profiles = profiles;
    }

    @Override
    public WorldBounds bounds() {
        return materials.bounds();
    }

    @Override
    public SoilHydraulicProfile find(int x, int y, int z) {
        return profiles.find(materials.materialAt(x, y, z));
    }
}
