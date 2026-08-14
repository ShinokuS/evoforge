package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

/** World fixture for the closed-loop multi-level MoveTo patrol demonstration. */
public final class MoveToPatrolScenario implements VisualizerScenario {

    @Override
    public String id() {
        return "movement-patrol";
    }

    @Override
    public String title() {
        return "Movement Patrol";
    }

    @Override
    public String description() {
        return "One mover loops across Z0-Z4; use PgUp/PgDn to follow its remaining MoveTo route.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(
                "scenario:patrol_ground");
        ObjectDefinitionId moverDefinition = assembly.objectDefinition(
                "scenario:patrol_mover");

        assembly.movementRate(moverDefinition, 500);
        assembly.exclusiveOccupancy(moverDefinition);
        MoveToScenarioCourse.build(assembly, ground);

        ObjectId mover = assembly.createObject(moverDefinition);
        assembly.placeObject(mover, -4, -2, 0);

        SimulationRuntime runtime = assembly.start();
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 5f, 0f, 0.85f),
                new MoveToPatrolController(runtime, mover));
    }
}
