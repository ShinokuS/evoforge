package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.mechanics.terrainmutation.TerrainMutationWorkflow;
import io.github.evoforge.simulation.world.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.soil.TerrainSoilPropertiesLookup;
import io.github.evoforge.simulation.world.soil.storage.SparseSoilLiquidStorage;
import io.github.evoforge.simulation.world.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.liquid.water.WaterSystem;
import io.github.evoforge.simulation.world.geometry.WorldGeometryLookup;
import io.github.evoforge.simulation.mechanics.movement.MovementStateStore;
import io.github.evoforge.simulation.world.space.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.navigation.NavigationSystem;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.space.placement.ObjectPlacementSystem;
import io.github.evoforge.simulation.world.space.position.PositionSystem;
import io.github.evoforge.simulation.world.space.position.CellPositionIndex;
import io.github.evoforge.simulation.world.space.orientation.OrientationSystem;

/** Authoritative mutable world stores and base systems that exist before runtime scheduling starts. */
final class SimulationWorldState {
    final TerrainMutationWorkflow landscape;
    final WorldGeometryLookup geometry;
    final LiquidSystem liquids;
    final PreStartSoilPropertiesLookup soilProperties;
    final SoilLiquidSystem soilLiquids;
    final WaterSystem water;
    final NavigationSystem navigation;
    final ObjectRepository objects;
    final ObjectFactory objectFactory;
    final CellPositionIndex cells;
    final PositionSystem spatial;
    final OrientationSystem orientations;
    final OccupancySystem occupancy;
    final ObjectPlacementSystem objectPlacement;
    final MovementStateStore movementState;

    SimulationWorldState(SimulationDefinitions definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("definitions must not be null");
        }
        landscape = TerrainMutationWorkflow.create(new SparseTerrainStorage(), definitions.landscape);
        geometry = new WorldGeometryLookup(landscape.geometry());
        liquids = new LiquidSystem(new SparseLiquidStorage(), geometry);
        soilProperties = new PreStartSoilPropertiesLookup(
                new TerrainSoilPropertiesLookup(
                        landscape.terrain(),
                        definitions.soilProperties));
        soilLiquids = new SoilLiquidSystem(
                new SparseSoilLiquidStorage(),
                soilProperties,
                definitions.liquidTransport);
        water = new WaterSystem(liquids);
        navigation = new NavigationSystem(geometry);
        objects = new ObjectRepository();
        objectFactory = new ObjectFactory(objects, definitions.objects);
        cells = new CellPositionIndex();
        spatial = new PositionSystem(cells);
        orientations = new OrientationSystem(objects);
        occupancy = new OccupancySystem(objects, cells.lookup(), definitions.occupancy);
        objectPlacement = new ObjectPlacementSystem(objects, occupancy, spatial);
        movementState = new MovementStateStore();
    }
}
