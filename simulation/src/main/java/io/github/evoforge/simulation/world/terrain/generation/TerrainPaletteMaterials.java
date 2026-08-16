package io.github.evoforge.simulation.world.terrain.generation;

/** Material choices exposed by the first natural-terrain palette contract. */
public record TerrainPaletteMaterials(
        TerrainMaterialKey topsoil,
        TerrainMaterialKey soil,
        TerrainMaterialKey sand,
        TerrainMaterialKey rock) {

    public TerrainPaletteMaterials {
        if (topsoil == null || soil == null || sand == null || rock == null) {
            throw new IllegalArgumentException(
                    "terrain palette materials must not be null");
        }
    }
}
