package io.github.evoforge.simulation.world.navigation.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.geometry.ShapeTraversalFactor;
import io.github.evoforge.simulation.world.geometry.TransitionMask;
import io.github.evoforge.simulation.world.geometry.TransitionPorts;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import io.github.evoforge.simulation.world.navigation.traversal.MaterialTraversalDefinitions;

final class TransitionCostCalculatorTest {

    @Test
    void averagesBothSurfaceCosts() {
        Fixture fixture = new Fixture();
        MaterialDefinitionId source = fixture.definition(0, 1000);
        MaterialDefinitionId destination = fixture.definition(1, 1600);

        fixture.cell(0, 0, -1, source, FullShape.INSTANCE);
        fixture.cell(1, 0, -1, destination, FullShape.INSTANCE);

        assertEquals(
                1300,
                fixture.calculator().cost(
                        0, 0, 0,
                        1, 0, 0)
                        .units());
    }

    @Test
    void appliesGridLengthAfterLocalAverage() {
        Fixture fixture = new Fixture();
        MaterialDefinitionId ground = fixture.definition(0, 1000);

        fixture.cell(0, 0, -1, ground, FullShape.INSTANCE);
        fixture.cell(1, 1, -1, ground, FullShape.INSTANCE);

        assertEquals(
                1414,
                fixture.calculator().cost(
                        0, 0, 0,
                        1, 1, 0)
                        .units());
    }

    @Test
    void keepsDepartureAndArrivalContributionsOwnedByTheirShapes() {
        Fixture fixture = new Fixture();
        MaterialDefinitionId ground = fixture.definition(0, 1000);

        fixture.cell(
                0,
                0,
                -1,
                ground,
                new DirectedFactorShape(
                        1500,
                        900));
        fixture.cell(
                1,
                0,
                -1,
                ground,
                new DirectedFactorShape(
                        1200,
                        1100));

        assertEquals(
                1300,
                fixture.calculator().cost(
                        0, 0, 0,
                        1, 0, 0)
                        .units());

        assertEquals(
                1050,
                fixture.calculator().cost(
                        1, 0, 0,
                        0, 0, 0)
                        .units());
    }

    @Test
    void failsWhenTraversalDefinitionIsMissing() {
        Fixture fixture = new Fixture();
        MaterialDefinitionId source = fixture.definition(0, 1000);
        MaterialDefinitionId destination = MaterialDefinitionId.of(1);

        fixture.cell(0, 0, -1, source, FullShape.INSTANCE);
        fixture.cell(1, 0, -1, destination, FullShape.INSTANCE);

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.calculator().cost(
                        0, 0, 0,
                        1, 0, 0));
    }

    @Test
    void rejectsNonAdjacentPositions() {
        Fixture fixture = new Fixture();

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.calculator().cost(
                        0, 0, 0,
                        2, 0, 0));
    }

    private record Cell(
            int x,
            int y,
            int z) {
    }

    private static final class Fixture {

        private final Map<Cell, MaterialDefinitionId> terrain =
                new HashMap<>();
        private final Map<Cell, Shape> geometry =
                new HashMap<>();
        private final MaterialTraversalDefinitions definitions =
                new MaterialTraversalDefinitions();

        MaterialDefinitionId definition(
                int id,
                long cost) {

            MaterialDefinitionId definitionId =
                    MaterialDefinitionId.of(id);
            definitions.put(
                    definitionId,
                    SurfaceTraversalCost.of(cost));
            return definitionId;
        }

        void cell(
                int x,
                int y,
                int z,
                MaterialDefinitionId definitionId,
                Shape shape) {

            Cell cell = new Cell(x, y, z);
            terrain.put(cell, definitionId);
            geometry.put(cell, shape);
        }

        TransitionCostCalculator calculator() {
            TerrainLookup terrainLookup =
                    (x, y, z) -> terrain.get(
                            new Cell(x, y, z));
            GeometryLookup geometryLookup =
                    (x, y, z) -> geometry.get(
                            new Cell(x, y, z));

            return new TransitionCostCalculator(
                    terrainLookup,
                    geometryLookup,
                    definitions);
        }
    }

    private static final class DirectedFactorShape
            implements Shape {

        private static final int EAST =
                TransitionMask.of(1, 0, 0);
        private static final int WEST =
                TransitionMask.of(-1, 0, 0);

        private final int departureFactor;
        private final int arrivalFactor;

        private DirectedFactorShape(
                int departureFactor,
                int arrivalFactor) {

            this.departureFactor =
                    ShapeTraversalFactor.requirePositive(
                            departureFactor);
            this.arrivalFactor =
                    ShapeTraversalFactor.requirePositive(
                            arrivalFactor);
        }

        @Override
        public long transitionPorts(
                int relativeX,
                int relativeY,
                int relativeZ) {

            if (relativeX == 0
                    && relativeY == 0
                    && relativeZ == 1) {
                return TransitionPorts.departuresOnly(
                        EAST | WEST);
            }

            if (relativeY == 0
                    && relativeZ == 1
                    && Math.abs(relativeX) == 1) {
                return TransitionPorts.arrivalsOnly(
                        TransitionMask.of(
                                -relativeX,
                                0,
                                0));
            }

            return TransitionPorts.NONE;
        }

        @Override
        public int departureTraversalFactor(
                int relativeX,
                int relativeY,
                int relativeZ,
                int directionX,
                int directionY,
                int directionZ) {

            int owned = Shape.super.departureTraversalFactor(
                    relativeX,
                    relativeY,
                    relativeZ,
                    directionX,
                    directionY,
                    directionZ);

            return owned == ShapeTraversalFactor.NONE
                    ? ShapeTraversalFactor.NONE
                    : departureFactor;
        }

        @Override
        public int arrivalTraversalFactor(
                int relativeX,
                int relativeY,
                int relativeZ,
                int directionX,
                int directionY,
                int directionZ) {

            int owned = Shape.super.arrivalTraversalFactor(
                    relativeX,
                    relativeY,
                    relativeZ,
                    directionX,
                    directionY,
                    directionZ);

            return owned == ShapeTraversalFactor.NONE
                    ? ShapeTraversalFactor.NONE
                    : arrivalFactor;
        }
    }
}
