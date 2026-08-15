package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.world.agent.decision.AgentDecisionLookup;
import io.github.evoforge.simulation.world.agent.need.NeedLookup;
import io.github.evoforge.simulation.world.agent.need.progression.NeedProgressionLookup;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionLookup;
import io.github.evoforge.simulation.world.agent.search.AgentSearchLookup;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidLookup;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesLookup;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainExtentLookup;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainRevisionLookup;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSurfaceLookup;
import io.github.evoforge.simulation.world.landscape.water.SurfaceWaterStorageLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterFlowLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterSurfaceLookup;
import io.github.evoforge.simulation.world.mechanics.consumption.ConsumableStockLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.growth.GrowthLookup;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToView;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.pathfinding.Pathfinder;
import io.github.evoforge.simulation.world.spatial.CellObjectLookup;
import io.github.evoforge.simulation.world.spatial.TransformLookup;
import io.github.evoforge.simulation.world.spatial.orientation.OrientationLookup;

/** Read-only capabilities exposed by a started simulation runtime. */
public record SimulationView(
        ObjectLookup objects,
        TransformLookup transforms,
        OrientationLookup orientations,
        VisionLookup vision,
        TerrainLookup terrain,
        TerrainExtentLookup terrainExtents,
        TerrainSurfaceLookup terrainSurfaces,
        TerrainRevisionLookup terrainRevision,
        GeometryLookup geometry,
        SoilLiquidLookup soilLiquids,
        SoilPropertiesLookup soilProperties,
        SurfaceWaterStorageLookup surfaceWaterStorage,
        WaterLookup water,
        WaterSurfaceLookup waterSurfaces,
        WaterFlowLookup waterFlow,
        NavigationLookup navigation,
        OccupancyLookup occupancy,
        CellObjectLookup cells,
        Pathfinder pathfinder,
        MoveToView moveTo,
        NeedLookup needs,
        NeedProgressionLookup needProgression,
        ConsumableStockLookup consumableStocks,
        GrowthLookup growth,
        AgentDecisionLookup agents,
        AgentSearchLookup searches) {

    public SimulationView {
        if (objects == null || transforms == null || orientations == null || vision == null
                || terrain == null || terrainExtents == null || terrainSurfaces == null
                || terrainRevision == null || geometry == null || soilLiquids == null
                || soilProperties == null || surfaceWaterStorage == null
                || water == null || waterSurfaces == null || waterFlow == null
                || navigation == null || occupancy == null || cells == null
                || pathfinder == null || moveTo == null || needs == null
                || needProgression == null || consumableStocks == null
                || growth == null || agents == null || searches == null) {
            throw new IllegalArgumentException("simulation view capabilities must not be null");
        }
    }
}
