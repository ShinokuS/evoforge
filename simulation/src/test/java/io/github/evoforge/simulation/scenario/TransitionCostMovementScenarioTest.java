package io.github.evoforge.simulation.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepResult;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.ShapeTraversalFactor;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class TransitionCostMovementScenarioTest {

    @Test
    void surfaceCostsChangeAuthoritativeStepDuration() {
        ScenarioBuilder builder = ScenarioBuilder.create();
        LandscapeDefinitionId source =
                builder.landscapeDefinition(
                        "test:source",
                        1000);
        LandscapeDefinitionId destination =
                builder.landscapeDefinition(
                        "test:destination",
                        1600);
        ObjectDefinitionId walker =
                builder.objectDefinition("test:walker");
        builder.movementRate(walker, 100);

        builder.placeTerrain(0, 0, -1, source);
        builder.placeTerrain(1, 0, -1, destination);

        ObjectId objectId = builder.createObject(walker);
        builder.placeObject(objectId, 0, 0, 0);

        ScenarioHarness scenario = builder.start();

        MoveStepResult result = scenario.submit(
                new MoveStepCommand(
                        objectId,
                        1,
                        0,
                        0));

        assertTrue(result.accepted());

        scenario.advanceTicks(12);
        assertEquals(0, scenario.transforms().x(objectId));

        scenario.advance();
        assertEquals(1, scenario.transforms().x(objectId));
    }

    @Test
    void shapeArrivalFactorChangesAuthoritativeStepDuration() {
        ScenarioBuilder builder = ScenarioBuilder.create();
        LandscapeDefinitionId ground =
                builder.landscapeDefinition("test:ground");
        ObjectDefinitionId walker =
                builder.objectDefinition("test:walker");
        builder.movementRate(walker, 100);

        builder.placeTerrain(0, 0, -1, ground);
        builder.placeTerrain(1, 0, -1, ground);
        builder.setShape(
                1,
                0,
                -1,
                new ArrivalPenaltyShape());

        ObjectId objectId = builder.createObject(walker);
        builder.placeObject(objectId, 0, 0, 0);

        ScenarioHarness scenario = builder.start();

        MoveStepResult result = scenario.submit(
                new MoveStepCommand(
                        objectId,
                        1,
                        0,
                        0));

        assertTrue(result.accepted());

        scenario.advanceTicks(14);
        assertEquals(0, scenario.transforms().x(objectId));

        scenario.advance();
        assertEquals(1, scenario.transforms().x(objectId));
    }

    private static final class ArrivalPenaltyShape
            implements Shape {

        @Override
        public long transitionPorts(
                int relativeX,
                int relativeY,
                int relativeZ) {

            return FullShape.INSTANCE.transitionPorts(
                    relativeX,
                    relativeY,
                    relativeZ);
        }

        @Override
        public int transitionBlocks(
                int relativeX,
                int relativeY,
                int relativeZ) {

            return FullShape.INSTANCE.transitionBlocks(
                    relativeX,
                    relativeY,
                    relativeZ);
        }

        @Override
        public int arrivalTraversalFactor(
                int relativeX,
                int relativeY,
                int relativeZ,
                int directionX,
                int directionY,
                int directionZ) {

            int base = Shape.super.arrivalTraversalFactor(
                    relativeX,
                    relativeY,
                    relativeZ,
                    directionX,
                    directionY,
                    directionZ);

            if (base == ShapeTraversalFactor.NONE) {
                return ShapeTraversalFactor.NONE;
            }

            return Math.multiplyExact(
                    base,
                    2);
        }
    }
}
