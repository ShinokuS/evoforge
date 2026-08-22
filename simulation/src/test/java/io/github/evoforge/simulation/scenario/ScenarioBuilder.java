package io.github.evoforge.simulation.scenario;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.navigation.traversal.SurfaceTraversalCost;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

public final class ScenarioBuilder {

    private final SimulationAssembly assembly;

    private ScenarioBuilder() {
        assembly = SimulationAssembly.create();
    }

    public static ScenarioBuilder create() {
        return new ScenarioBuilder();
    }

    public MaterialDefinitionId landscapeDefinition(
            String key) {

        return landscapeDefinition(
                key,
                SurfaceTraversalCost.NEUTRAL_UNITS);
    }

    public MaterialDefinitionId landscapeDefinition(
            String key,
            long traversalCostUnits) {

        return assembly.landscapeDefinition(
                key,
                traversalCostUnits);
    }

    public ObjectDefinitionId objectDefinition(
            String key) {

        return assembly.objectDefinition(key);
    }

    public ScenarioBuilder movementRate(
            ObjectDefinitionId definitionId,
            long unitsPerTick) {

        assembly.movementRate(
                definitionId,
                unitsPerTick);

        return this;
    }

    public ObjectId createObject(
            ObjectDefinitionId definitionId) {

        return assembly.createObject(definitionId);
    }

    public ScenarioBuilder placeObject(
            ObjectId objectId,
            int x,
            int y,
            int z) {

        assembly.placeObject(
                objectId,
                x,
                y,
                z);

        return this;
    }

    public ScenarioBuilder placeTerrain(
            int x,
            int y,
            int z,
            MaterialDefinitionId definitionId) {

        assembly.placeTerrain(
                x,
                y,
                z,
                definitionId);

        return this;
    }

    public ScenarioBuilder setShape(
            int x,
            int y,
            int z,
            Shape shape) {

        assembly.setShape(
                x,
                y,
                z,
                shape);

        return this;
    }

    public ScenarioHarness start() {
        return new ScenarioHarness(
                assembly.start());
    }
}
