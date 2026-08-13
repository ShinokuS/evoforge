package io.github.evoforge.simulation.world.mechanics.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.object.placement.ObjectPlacementResult;
import io.github.evoforge.simulation.world.object.placement.ObjectPlacementSystem;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;
import org.junit.jupiter.api.Test;

final class OccupancySystemTest {

    @Test
    void exclusiveOccupancyIsDerivedFromSpatialAndTransparentObjectsMayShare() {
        Fixture fixture = fixture();
        ObjectId bush = fixture.factory.create(fixture.bush).id();
        ObjectId cow = fixture.factory.create(fixture.cow).id();
        ObjectId secondBush = fixture.factory.create(fixture.bush).id();

        assertEquals(
                ObjectPlacementResult.PLACED,
                fixture.placement.place(bush, 4, 5, 6));
        assertEquals(
                OccupancyState.FREE,
                fixture.occupancy.state(4, 5, 6));

        assertEquals(
                ObjectPlacementResult.PLACED,
                fixture.placement.place(cow, 4, 5, 6));
        assertEquals(
                ObjectPlacementResult.PLACED,
                fixture.placement.place(secondBush, 4, 5, 6));
        assertEquals(
                OccupancyState.OCCUPIED,
                fixture.occupancy.state(4, 5, 6));
        assertEquals(
                OccupancyState.FREE,
                fixture.occupancy.admissionState(secondBush, 4, 5, 6));
    }

    @Test
    void secondExclusivePlacementIsRejectedWithoutSpatialMutation() {
        Fixture fixture = fixture();
        ObjectId first = fixture.factory.create(fixture.cow).id();
        ObjectId second = fixture.factory.create(fixture.cow).id();

        assertEquals(
                ObjectPlacementResult.PLACED,
                fixture.placement.place(first, 0, 0, 0));
        assertEquals(
                ObjectPlacementResult.DESTINATION_OCCUPIED,
                fixture.placement.place(second, 0, 0, 0));
        assertFalse(fixture.spatial.transforms().has(second));
        assertEquals(
                OccupancyState.OCCUPIED,
                fixture.occupancy.state(0, 0, 0));
    }

    @Test
    void reservationBlocksExclusiveCandidateButNotTransparentObject() {
        Fixture fixture = fixture();
        ObjectId claimant = fixture.factory.create(fixture.cow).id();
        ObjectId otherCow = fixture.factory.create(fixture.cow).id();
        ObjectId bush = fixture.factory.create(fixture.bush).id();
        fixture.placement.place(claimant, 0, 0, 0);

        OccupancyReservationAttempt attempt =
                fixture.occupancy.tryReserve(
                        claimant,
                        1,
                        0,
                        0);
        assertEquals(
                OccupancyReservationResult.ACQUIRED,
                attempt.result());
        OccupancyReservationId reservationId = attempt.reservationId();

        assertEquals(
                OccupancyState.RESERVED,
                fixture.occupancy.state(1, 0, 0));

        assertEquals(
                ObjectPlacementResult.PLACED,
                fixture.placement.place(bush, 1, 0, 0));
        assertEquals(
                ObjectPlacementResult.DESTINATION_RESERVED,
                fixture.placement.place(otherCow, 1, 0, 0));

        assertFalse(fixture.occupancy.release(
                OccupancyReservationId.of(
                        reservationId.asLong() + 1),
                claimant,
                1,
                0,
                0));
        assertEquals(
                OccupancyState.RESERVED,
                fixture.occupancy.state(1, 0, 0));

        assertTrue(fixture.occupancy.release(
                reservationId,
                claimant,
                1,
                0,
                0));
        assertEquals(
                OccupancyState.FREE,
                fixture.occupancy.state(1, 0, 0));
        assertEquals(0, fixture.occupancy.reservationCount());
    }

    @Test
    void transparentObjectDoesNotNeedReservationEvenInOccupiedCell() {
        Fixture fixture = fixture();
        ObjectId cow = fixture.factory.create(fixture.cow).id();
        ObjectId bush = fixture.factory.create(fixture.bush).id();
        fixture.placement.place(cow, 2, 0, 0);

        OccupancyReservationAttempt attempt =
                fixture.occupancy.tryReserve(
                        bush,
                        2,
                        0,
                        0);

        assertEquals(
                OccupancyReservationResult.NOT_REQUIRED,
                attempt.result());
        assertNull(attempt.reservationId());
        assertEquals(0, fixture.occupancy.reservationCount());
        assertEquals(
                OccupancyState.FREE,
                fixture.occupancy.admissionState(bush, 2, 0, 0));
    }

    @Test
    void occupancyOwnsReservationIdentity() {
        Fixture fixture = fixture();
        ObjectId first = fixture.factory.create(fixture.cow).id();
        ObjectId second = fixture.factory.create(fixture.cow).id();

        OccupancyReservationAttempt firstAttempt =
                fixture.occupancy.tryReserve(first, 7, 0, 0);
        OccupancyReservationAttempt secondAttempt =
                fixture.occupancy.tryReserve(second, 8, 0, 0);

        assertEquals(
                OccupancyReservationResult.ACQUIRED,
                firstAttempt.result());
        assertEquals(
                OccupancyReservationResult.ACQUIRED,
                secondAttempt.result());
        assertFalse(firstAttempt.reservationId().equals(
                secondAttempt.reservationId()));
    }

    private static Fixture fixture() {
        DefinitionRegistry<ObjectDefinitionId> definitions =
                new DefinitionRegistry<>(
                        ObjectDefinitionId::of,
                        ObjectDefinitionId::asInt);
        ObjectDefinitionId cow = definitions.register("test:cow");
        ObjectDefinitionId bush = definitions.register("test:bush");
        definitions.freeze();

        ObjectRepository objects = new ObjectRepository();
        ObjectFactory factory = new ObjectFactory(objects, definitions);
        CellSpatialIndex cells = new CellSpatialIndex();
        SpatialSystem spatial = new SpatialSystem(cells);

        OccupancyDefinitions occupancyDefinitions =
                new OccupancyDefinitions();
        occupancyDefinitions.put(cow, true);
        occupancyDefinitions.freeze();

        OccupancySystem occupancy = new OccupancySystem(
                objects,
                cells.lookup(),
                occupancyDefinitions);
        ObjectPlacementSystem placement = new ObjectPlacementSystem(
                objects,
                occupancy,
                spatial);

        return new Fixture(
                cow,
                bush,
                factory,
                spatial,
                occupancy,
                placement);
    }

    private record Fixture(
            ObjectDefinitionId cow,
            ObjectDefinitionId bush,
            ObjectFactory factory,
            SpatialSystem spatial,
            OccupancySystem occupancy,
            ObjectPlacementSystem placement) {
    }
}
