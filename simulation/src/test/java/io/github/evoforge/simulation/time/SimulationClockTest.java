package io.github.evoforge.simulation.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationClockTest {

        @Test
        void startsAtZero() {
                SimulationClock clock = new SimulationClock();

                assertEquals(
                                0,
                                clock.tick());
        }

        @Test
        void advancesByOneTick() {
                SimulationClock clock = new SimulationClock();

                clock.advance();

                assertEquals(
                                1,
                                clock.tick());
        }

        @Test
        void advancesSequentially() {
                SimulationClock clock = new SimulationClock();

                clock.advance();
                clock.advance();
                clock.advance();

                assertEquals(
                                3,
                                clock.tick());
        }
}