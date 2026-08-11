package io.github.evoforge.simulation.world.landscape;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainPlacementResult;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainRemovalResult;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainReplacementResult;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometrySystem;

public final class LandscapeSystem implements LandscapeMutations {

    private final TerrainSystem terrain;
    private final GeometrySystem geometry;

    public LandscapeSystem(
            TerrainSystem terrain,
            GeometrySystem geometry) {

        if (terrain == null) {
            throw new IllegalArgumentException(
                    "terrain must not be null");
        }

        if (geometry == null) {
            throw new IllegalArgumentException(
                    "geometry must not be null");
        }

        this.terrain = terrain;
        this.geometry = geometry;
    }

    @Override
    public TerrainPlacementResult placeTerrain(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId) {

        TerrainPlacementResult result =
                terrain.place(
                        x,
                        y,
                        z,
                        definitionId);

        if (result.accepted()) {
            geometry.clearShapeOverride(
                    x,
                    y,
                    z);
        }

        return result;
    }

    @Override
    public TerrainReplacementResult replaceTerrain(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId) {

        return terrain.replace(
                x,
                y,
                z,
                definitionId);
    }

    @Override
    public TerrainRemovalResult removeTerrain(
            int x,
            int y,
            int z) {

        TerrainRemovalResult result =
                terrain.remove(
                        x,
                        y,
                        z);

        if (result.accepted()) {
            geometry.clearShapeOverride(
                    x,
                    y,
                    z);
        }

        return result;
    }
}
