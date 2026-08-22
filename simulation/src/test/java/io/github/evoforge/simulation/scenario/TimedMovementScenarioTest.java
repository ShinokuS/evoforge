package io.github.evoforge.simulation.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.mechanics.movement.command.MoveStepCommand;
import io.github.evoforge.simulation.mechanics.movement.command.MoveStepResult;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class TimedMovementScenarioTest {

    @Test
    void keepsSourcePositionUntilCompletionTick() {
        ScenarioBuilder builder = ScenarioBuilder.create();
        LandscapeDefinitionId ground =
                builder.landscapeDefinition("test:ground");
        ObjectDefinitionId walker =
                builder.objectDefinition("test:walker");
        builder.movementRate(walker, 100);

        ObjectId objectId = builder.createObject(walker);
        builder.placeObject(objectId, 0, 0, 0);
        support(builder, ground, 0, 0);
        support(builder, ground, 1, 0);

        ScenarioHarness scenario = builder.start();

        assertAccepted(
                scenario.submit(
                        new MoveStepCommand(
                                objectId,
                                1,
                                0,
                                0)));

        scenario.advanceTicks(9);

        assertPosition(
                scenario,
                objectId,
                0,
                0,
                0);

        scenario.advance();

        assertPosition(
                scenario,
                objectId,
                1,
                0,
                0);
        assertEquals(10, scenario.tick());
    }

    @Test
    void fasterObjectCompletesSameTransitionEarlier() {
        ScenarioBuilder builder = ScenarioBuilder.create();
        LandscapeDefinitionId ground =
                builder.landscapeDefinition("test:ground");
        ObjectDefinitionId human =
                builder.objectDefinition("test:human");
        ObjectDefinitionId horse =
                builder.objectDefinition("test:horse");
        builder.movementRate(human, 100);
        builder.movementRate(horse, 200);

        ObjectId humanId = builder.createObject(human);
        ObjectId horseId = builder.createObject(horse);
        builder.placeObject(humanId, 0, 0, 0);
        builder.placeObject(horseId, 0, 2, 0);

        support(builder, ground, 0, 0);
        support(builder, ground, 1, 0);
        support(builder, ground, 0, 2);
        support(builder, ground, 1, 2);

        ScenarioHarness scenario = builder.start();

        assertAccepted(
                scenario.submit(
                        new MoveStepCommand(
                                humanId,
                                1,
                                0,
                                0)));
        assertAccepted(
                scenario.submit(
                        new MoveStepCommand(
                                horseId,
                                1,
                                2,
                                0)));

        scenario.advanceTicks(5);

        assertPosition(
                scenario,
                humanId,
                0,
                0,
                0);
        assertPosition(
                scenario,
                horseId,
                1,
                2,
                0);

        scenario.advanceTicks(5);

        assertPosition(
                scenario,
                humanId,
                1,
                0,
                0);
    }

    @Test
    void diagonalTransitionTakesLongerThanCardinalTransition() {
        ScenarioBuilder builder = ScenarioBuilder.create();
        LandscapeDefinitionId ground =
                builder.landscapeDefinition("test:ground");
        ObjectDefinitionId walker =
                builder.objectDefinition("test:walker");
        builder.movementRate(walker, 100);

        ObjectId cardinal = builder.createObject(walker);
        ObjectId diagonal = builder.createObject(walker);
        builder.placeObject(cardinal, 0, 0, 0);
        builder.placeObject(diagonal, 0, 3, 0);

        support(builder, ground, 0, 0);
        support(builder, ground, 1, 0);
        support(builder, ground, 0, 3);
        support(builder, ground, 1, 4);

        ScenarioHarness scenario = builder.start();

        scenario.submit(
                new MoveStepCommand(
                        cardinal,
                        1,
                        0,
                        0));
        scenario.submit(
                new MoveStepCommand(
                        diagonal,
                        1,
                        4,
                        0));

        scenario.advanceTicks(10);

        assertPosition(
                scenario,
                cardinal,
                1,
                0,
                0);
        assertPosition(
                scenario,
                diagonal,
                0,
                3,
                0);

        scenario.advanceTicks(4);

        assertPosition(
                scenario,
                diagonal,
                1,
                4,
                0);
    }

    @Test
    void rejectsSecondStepWhileMovementIsActive() {
        ScenarioRun run = movingScenario(100);

        assertAccepted(
                run.scenario().submit(
                        new MoveStepCommand(
                                run.objectId(),
                                1,
                                0,
                                0)));

        assertRejected(
                run.scenario().submit(
                        new MoveStepCommand(
                                run.objectId(),
                                1,
                                0,
                                0)),
                "movement:already_moving");
    }

    @Test
    void rejectsObjectWithoutMovementCapability() {
        ScenarioBuilder builder = ScenarioBuilder.create();
        LandscapeDefinitionId ground =
                builder.landscapeDefinition("test:ground");
        ObjectDefinitionId staticObject =
                builder.objectDefinition("test:static");
        ObjectId objectId = builder.createObject(staticObject);
        builder.placeObject(objectId, 0, 0, 0);
        support(builder, ground, 0, 0);
        support(builder, ground, 1, 0);

        ScenarioHarness scenario = builder.start();

        assertRejected(
                scenario.submit(
                        new MoveStepCommand(
                                objectId,
                                1,
                                0,
                                0)),
                "movement:movement_unavailable");
    }

    @Test
    void rejectsStructurallyUnavailableTransition() {
        ScenarioBuilder builder = ScenarioBuilder.create();
        LandscapeDefinitionId ground =
                builder.landscapeDefinition("test:ground");
        ObjectDefinitionId walker =
                builder.objectDefinition("test:walker");
        builder.movementRate(walker, 100);
        ObjectId objectId = builder.createObject(walker);
        builder.placeObject(objectId, 0, 0, 0);
        support(builder, ground, 0, 0);

        ScenarioHarness scenario = builder.start();

        assertRejected(
                scenario.submit(
                        new MoveStepCommand(
                                objectId,
                                1,
                                0,
                                0)),
                "movement:transition_unavailable");
    }

    @Test
    void carriesFractionalTimingAcrossSteps() {
        ScenarioBuilder builder = ScenarioBuilder.create();
        LandscapeDefinitionId ground =
                builder.landscapeDefinition("test:ground");
        ObjectDefinitionId runner =
                builder.objectDefinition("test:runner");
        builder.movementRate(runner, 300);
        ObjectId objectId = builder.createObject(runner);
        builder.placeObject(objectId, 0, 0, 0);

        for (int x = 0; x <= 3; x++) {
            support(builder, ground, x, 0);
        }

        ScenarioHarness scenario = builder.start();

        scenario.submit(
                new MoveStepCommand(
                        objectId,
                        1,
                        0,
                        0));
        scenario.advanceTicks(3);
        assertPosition(scenario, objectId, 1, 0, 0);

        scenario.submit(
                new MoveStepCommand(
                        objectId,
                        2,
                        0,
                        0));
        scenario.advanceTicks(3);
        assertPosition(scenario, objectId, 2, 0, 0);

        scenario.submit(
                new MoveStepCommand(
                        objectId,
                        3,
                        0,
                        0));
        scenario.advanceTicks(3);
        assertPosition(scenario, objectId, 2, 0, 0);

        scenario.advance();
        assertPosition(scenario, objectId, 3, 0, 0);
        assertEquals(10, scenario.tick());
    }

    @Test
    void batchedAndIndividualTickAdvancementAreEquivalent() {
        ScenarioRun batched = movingScenario(100);
        ScenarioRun individual = movingScenario(100);

        batched.scenario().submit(
                new MoveStepCommand(
                        batched.objectId(),
                        1,
                        0,
                        0));
        individual.scenario().submit(
                new MoveStepCommand(
                        individual.objectId(),
                        1,
                        0,
                        0));

        batched.scenario().advanceTicks(10);

        for (int tick = 0; tick < 10; tick++) {
            individual.scenario().advance();
        }

        assertEquals(
                batched.scenario().tick(),
                individual.scenario().tick());
        assertEquals(
                batched.scenario().transforms().x(
                        batched.objectId()),
                individual.scenario().transforms().x(
                        individual.objectId()));
        assertEquals(
                batched.scenario().transforms().y(
                        batched.objectId()),
                individual.scenario().transforms().y(
                        individual.objectId()));
        assertEquals(
                batched.scenario().transforms().z(
                        batched.objectId()),
                individual.scenario().transforms().z(
                        individual.objectId()));
    }

    private static void assertAccepted(
            MoveStepResult result) {
        assertTrue(result.accepted(), result.code().toString());
    }

    private static void assertRejected(
            MoveStepResult result,
            String code) {
        assertFalse(result.accepted());
        assertEquals(code, result.code().value());
    }

    private static ScenarioRun movingScenario(
            long rate) {

        ScenarioBuilder builder = ScenarioBuilder.create();
        LandscapeDefinitionId ground =
                builder.landscapeDefinition("test:ground");
        ObjectDefinitionId walker =
                builder.objectDefinition("test:walker");
        builder.movementRate(walker, rate);
        ObjectId objectId = builder.createObject(walker);
        builder.placeObject(objectId, 0, 0, 0);
        support(builder, ground, 0, 0);
        support(builder, ground, 1, 0);

        return new ScenarioRun(
                builder.start(),
                objectId);
    }

    private static void support(
            ScenarioBuilder builder,
            LandscapeDefinitionId ground,
            int x,
            int y) {

        builder.placeTerrain(
                x,
                y,
                -1,
                ground);
    }

    private static void assertPosition(
            ScenarioHarness scenario,
            ObjectId objectId,
            int x,
            int y,
            int z) {

        assertEquals(
                x,
                scenario.transforms().x(objectId));
        assertEquals(
                y,
                scenario.transforms().y(objectId));
        assertEquals(
                z,
                scenario.transforms().z(objectId));
    }

    private record ScenarioRun(
            ScenarioHarness scenario,
            ObjectId objectId) {
    }
}
