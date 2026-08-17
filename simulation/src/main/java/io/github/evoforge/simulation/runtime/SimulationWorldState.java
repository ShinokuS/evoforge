package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.landscape.soil.TerrainSoilPropertiesLookup;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilLiquidStorage;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.WorldGeometryLookup;
import io.github.evoforge.simulation.world.mechanics.movement.MovementStateStore;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.navigation.NavigationSystem;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.placement.ObjectPlacementSystem;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;
import io.github.evoforge.simulation.world.spatial.orientation.OrientationSystem;

/** Authoritative mutable world stores and base systems that exist before runtime scheduling starts. */
final class SimulationWorldState {
    final LandscapeSystem landscape;
    final WorldGeometryLookup geometry;
    final LiquidSystem liquids;
    final PreStartSoilPropertiesLookup soilProperties;
    final SoilLiquidSystem soilLiquids;
    final WaterSystem water;
    final NavigationSystem navigation;
    final ObjectRepository objects;
    final ObjectFactory objectFactory;
    final CellSpatialIndex cells;
    final SpatialSystem spatial;
    final OrientationSystem orientations;
    final OccupancySystem occupancy;
    final ObjectPlacementSystem objectPlacement;
    final MovementStateStore movementState;

    SimulationWorldState(SimulationDefinitions definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("definitions must not be null");
        }
        landscape = LandscapeSystem.create(new SparseTerrainStorage(), definitions.landscape);
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
        cells = new CellSpatialIndex();
        spatial = new SpatialSystem(cells);
        orientations = new OrientationSystem(objects);
        occupancy = new OccupancySystem(objects, cells.lookup(), definitions.occupancy);
        objectPlacement = new ObjectPlacementSystem(objects, occupancy, spatial);
        movementState = new MovementStateStore();
    }
}
