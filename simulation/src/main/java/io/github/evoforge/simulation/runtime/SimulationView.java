package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.agents.decision.AgentDecisionLookup;
import io.github.evoforge.simulation.agents.need.NeedLookup;
import io.github.evoforge.simulation.agents.need.progression.NeedProgressionLookup;
import io.github.evoforge.simulation.agents.perception.vision.VisionLookup;
import io.github.evoforge.simulation.agents.search.AgentSearchLookup;
import io.github.evoforge.simulation.world.liquid.LiquidSurfaceRetentionLookup;
import io.github.evoforge.simulation.world.soil.SoilLiquidLookup;
import io.github.evoforge.simulation.world.soil.SoilPropertiesLookup;
import io.github.evoforge.simulation.world.terrain.TerrainExtentLookup;
import io.github.evoforge.simulation.world.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.terrain.TerrainRevisionLookup;
import io.github.evoforge.simulation.world.terrain.TerrainSurfaceLookup;
import io.github.evoforge.simulation.world.liquid.water.WaterFlowLookup;
import io.github.evoforge.simulation.world.liquid.water.WaterLookup;
import io.github.evoforge.simulation.world.liquid.water.WaterSurfaceLookup;
import io.github.evoforge.simulation.world.object.stock.ConsumableStockLookup;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.object.stock.growth.GrowthLookup;
import io.github.evoforge.simulation.mechanics.movement.MoveToView;
import io.github.evoforge.simulation.world.space.occupancy.OccupancyLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.navigation.pathfinding.Pathfinder;
import io.github.evoforge.simulation.world.space.position.CellObjectLookup;
import io.github.evoforge.simulation.world.space.position.PositionLookup;
import io.github.evoforge.simulation.world.space.orientation.OrientationLookup;

/** Read-only capabilities exposed by a started simulation runtime. */
public record SimulationView(
        ObjectLookup objects,
        PositionLookup positions,
        OrientationLookup orientations,
        VisionLookup vision,
        TerrainLookup terrain,
        TerrainExtentLookup terrainExtents,
        TerrainSurfaceLookup terrainSurfaces,
        TerrainRevisionLookup terrainRevision,
        GeometryLookup geometry,
        SoilLiquidLookup soilLiquids,
        SoilPropertiesLookup soilProperties,
        LiquidSurfaceRetentionLookup surfaceRetention,
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
        if (objects == null || positions == null || orientations == null || vision == null
                || terrain == null || terrainExtents == null || terrainSurfaces == null
                || terrainRevision == null || geometry == null || soilLiquids == null
                || soilProperties == null || surfaceRetention == null
                || water == null || waterSurfaces == null || waterFlow == null
                || navigation == null || occupancy == null || cells == null
                || pathfinder == null || moveTo == null || needs == null
                || needProgression == null || consumableStocks == null
                || growth == null || agents == null || searches == null) {
            throw new IllegalArgumentException("simulation view capabilities must not be null");
        }
    }
}
