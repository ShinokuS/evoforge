package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.control.core.CommandDispatcher;
import io.github.evoforge.simulation.control.movement.CancelMoveToCommand;
import io.github.evoforge.simulation.control.movement.CancelMoveToHandler;
import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepHandler;
import io.github.evoforge.simulation.control.movement.MoveToCommand;
import io.github.evoforge.simulation.control.movement.MoveToHandler;
import io.github.evoforge.simulation.control.sync.SynchronousCommandGateway;
import io.github.evoforge.simulation.control.terrain.PlaceTerrainCommand;
import io.github.evoforge.simulation.control.terrain.PlaceTerrainHandler;
import io.github.evoforge.simulation.control.terrain.ReplaceTerrainCommand;
import io.github.evoforge.simulation.control.terrain.ReplaceTerrainHandler;
import io.github.evoforge.simulation.world.landscape.water.WaterFlowLookup;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;

/** Final one-way transition from configured pre-start state into a scheduled simulation runtime. */
final class SimulationRuntimeStarter {
    private SimulationRuntimeStarter() { }

    static SimulationRuntime start(
            SimulationDefinitions definitions,
            SimulationWorldState world,
            SimulationStartupConfig config) {
        definitions.freeze();
        attachInitialOrientations(definitions, world, config);

        RuntimeKernel kernel = new RuntimeKernel();
        EnvironmentRuntime environment = EnvironmentRuntimeAssembly.assemble(
                definitions,
                world,
                config,
                kernel);
        MovementRuntime movement = MovementRuntimeAssembly.assemble(
                definitions,
                world,
                kernel);
        AgentRuntime agents = AgentRuntimeAssembly.assemble(
                definitions,
                world,
                config,
                kernel,
                environment,
                movement);

        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatcher.register(
                PlaceTerrainCommand.class,
                new PlaceTerrainHandler(world.landscape));
        dispatcher.register(
                ReplaceTerrainCommand.class,
                new ReplaceTerrainHandler(world.landscape));
        dispatcher.register(
                MoveStepCommand.class,
                new MoveStepHandler(movement.movement()));
        dispatcher.register(
                MoveToCommand.class,
                new MoveToHandler(movement.moveTo()));
        dispatcher.register(
                CancelMoveToCommand.class,
                new CancelMoveToHandler(movement.moveTo()));

        SimulationView view = new SimulationView(
                world.objects,
                world.spatial.transforms(),
                world.orientations,
                agents.vision(),
                world.landscape.terrain(),
                world.landscape.terrainExtents(),
                world.landscape.terrainSurfaces(),
                world.landscape.terrainRevision(),
                world.geometry,
                world.soilLiquids.lookup(),
                world.soilProperties,
                environment.surfaceRetention(),
                world.water.lookup(),
                world.water.surfaces(),
                WaterFlowLookup.from(environment.liquidFlow().flowLookup()),
                world.navigation.lookup(),
                world.occupancy,
                world.cells.lookup(),
                movement.pathfinder(),
                movement.moveTo(),
                agents.needs(),
                agents.needProgression(),
                agents.consumableStocks(),
                agents.growth(),
                agents.agents(),
                agents.searches());

        return new SimulationRuntime(
                new SynchronousCommandGateway(dispatcher),
                kernel.clock,
                kernel.stepper,
                view);
    }

    private static void attachInitialOrientations(
            SimulationDefinitions definitions,
            SimulationWorldState world,
            SimulationStartupConfig config) {
        for (ObjectId objectId : config.createdObjects()) {
            WorldObject object = world.objects.get(objectId);
            if (object != null
                    && (definitions.vision.has(object.definitionId())
                    || config.initialFacing().containsKey(objectId))) {
                world.orientations.attach(
                        objectId,
                        config.initialFacing().getOrDefault(objectId, FacingDirection.EAST));
            }
        }
    }
}
