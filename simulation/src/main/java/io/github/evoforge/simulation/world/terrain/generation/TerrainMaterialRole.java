package io.github.evoforge.simulation.world.terrain.generation;

/** Semantic material role consumed by reusable terrain-generation presets. */
public enum TerrainMaterialRole {
    SURFACE("surface"),
    SUBSURFACE("subsurface"),
    SEDIMENT("sediment"),
    BEDROCK("bedrock");

    private final String authoredName;

    TerrainMaterialRole(String authoredName) {
        this.authoredName = authoredName;
    }

    public String authoredName() {
        return authoredName;
    }

    public static TerrainMaterialRole fromAuthoredName(String value) {
        for (TerrainMaterialRole role : values()) {
            if (role.authoredName.equals(value)) return role;
        }
        return null;
    }
}
