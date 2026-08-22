package io.github.evoforge.simulation.world.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class TransitionPortsTest {

    @Test
    void packsAndUnpacksDepartureAndArrivalMasks() {
        int departures =
                TransitionMask.of(1, 0, 0)
                        | TransitionMask.of(0, 1, 0);

        int arrivals =
                TransitionMask.of(-1, 0, 0)
                        | TransitionMask.of(0, -1, 0);

        long ports =
                TransitionPorts.of(
                        departures,
                        arrivals);

        assertEquals(
                departures,
                TransitionPorts.departures(ports));

        assertEquals(
                arrivals,
                TransitionPorts.arrivals(ports));
    }

    @Test
    void supportsSameDirectionInBothRoles() {
        int direction =
                TransitionMask.of(
                        1,
                        1,
                        0);

        long ports =
                TransitionPorts.of(
                        direction,
                        direction);

        assertEquals(
                direction,
                TransitionPorts.departures(ports));

        assertEquals(
                direction,
                TransitionPorts.arrivals(ports));
    }

    @Test
    void rejectsInvalidDepartureMask() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransitionPorts.of(
                        1 << 13,
                        TransitionMask.NONE));
    }

    @Test
    void rejectsInvalidArrivalMask() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransitionPorts.of(
                        TransitionMask.NONE,
                        1 << 13));
    }
}
