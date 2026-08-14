package io.github.evoforge.simulation.world.mechanics.traversal.water;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

final class WaterWadingConstraintTest {

    @Test
    void configuredMoverAllowsShallowAndRejectsDeeperDestination() {
        Fixture fixture = new Fixture((x, y, z) -> null);
        ObjectId mover = fixture.createMover(250_000);

        fixture.water.addAtMost(1, 0, 0, 200_000);
        assertTrue(fixture.constraint().allows(
                mover, 0, 0, 0, 1, 0, 0));

        fixture.water.addAtMost(1, 0, 0, 100_000);
        assertFalse(fixture.constraint().allows(
                mover, 0, 0, 0, 1, 0, 0));
    }

    @Test
    void missingWadingAspectPreservesWaterNeutralTraversal() {
        Fixture fixture = new Fixture((x, y, z) -> null);
        ObjectId mover = fixture.createUnconfiguredMover();
        fixture.water.addAtMost(1, 0, 0, 900_000);

        assertTrue(fixture.constraint().allows(
                mover, 0, 0, 0, 1, 0, 0));
    }

    @Test
    void fullStandingCellWithWaterAboveIsDeeperThanOneCell() {
        Fixture fixture = new Fixture((x, y, z) -> null);
        ObjectId mover = fixture.createMover(CellVolume.FULL);
        fixture.water.addAtMost(1, 0, 0, CellVolume.FULL);
        fixture.water.addAtMost(1, 0, 1, 1);

        assertFalse(fixture.constraint().allows(
                mover, 0, 0, 0, 1, 0, 0));
    }

    @Test
    void deepSourceDoesNotPreventEscapeToDryDestination() {
        Fixture fixture = new Fixture((x, y, z) -> null);
        ObjectId mover = fixture.createMover(100_000);
        fixture.water.addAtMost(0, 0, 0, 800_000);

        assertTrue(fixture.constraint().allows(
                mover, 0, 0, 0, 1, 0, 0));
    }

    @Test
    void destinationDepthUsesGenericCellSpaceProfile() {
        Shape halfFree = new Shape() {
            @Override
            public long transitionPorts(
                    int relativeX,
                    int relativeY,
                    int relativeZ) {
                return 0L;
            }

            @Override
            public int solidVolume() {
                return CellVolume.FULL / 2;
            }
        };
        Fixture fixture = new Fixture(
                (x, y, z) -> x == 1 && y == 0 && z == 0
                        ? halfFree
                        : null);
        ObjectId mover = fixture.createMover(150_000);
        fixture.water.addAtMost(1, 0, 0, 100_000);

        assertFalse(fixture.constraint().allows(
                mover, 0, 0, 0, 1, 0, 0));
    }

    private static final class Fixture {
        private final DefinitionRegistry<ObjectDefinitionId> catalog =
                new DefinitionRegistry<>(
                        ObjectDefinitionId::of,
                        ObjectDefinitionId::asInt);
        private final ObjectRepository objects = new ObjectRepository();
        private final ObjectFactory factory = new ObjectFactory(objects, catalog);
        private final WaterWadingDefinitions definitions =
                new WaterWadingDefinitions();
        private final GeometryLookup geometry;
        private final WaterSystem water;

        private Fixture(GeometryLookup geometry) {
            this.geometry = geometry;
            water = new WaterSystem(
                    new SparseWaterStorage(),
                    geometry);
        }

        private ObjectId createMover(int maxDepth) {
            ObjectDefinitionId id = catalog.register(
                    "test:wader_" + maxDepth);
            definitions.put(id, new WaterWadingProfile(maxDepth));
            return factory.create(id).id();
        }

        private ObjectId createUnconfiguredMover() {
            ObjectDefinitionId id = catalog.register("test:unconfigured");
            return factory.create(id).id();
        }

        private WaterWadingConstraint constraint() {
            return new WaterWadingConstraint(
                    objects,
                    definitions,
                    water.lookup(),
                    geometry);
        }
    }
}
