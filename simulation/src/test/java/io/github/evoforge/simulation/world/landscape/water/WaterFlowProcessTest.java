package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionPorts;

final class WaterFlowProcessTest {

    @Test
    void coalescesWakeupsAndStopsSchedulingAtDormancy() {
        Shape isolatedFreeCell = new Shape() {
            @Override
            public int solidVolume() {
                return CellVolume.EMPTY;
            }

            @Override
            public long transitionPorts(
                    int relativeX,
                    int relativeY,
                    int relativeZ) {
                return TransitionPorts.NONE;
            }
        };

        WaterSystem water = new WaterSystem(
                new SparseWaterStorage(),
                (x, y, z) -> isolatedFreeCell);
        WaterFlowSystem flow = new WaterFlowSystem(
                water,
                (x, y, z) -> isolatedFreeCell);
        WaterFlowProcess process = new WaterFlowProcess(flow);
        RecordingScheduler scheduler = new RecordingScheduler();
        process.bindScheduler(scheduler);

        water.addAtMost(0, 0, 0, 200_000);
        process.activate();
        process.activate();

        assertTrue(process.scheduled());
        assertEquals(1, scheduler.calls);
        assertEquals(1L, scheduler.delayTicks);
        assertEquals(0L, scheduler.processId);

        process.resume(0L);

        assertFalse(process.scheduled());
        assertEquals(1, scheduler.calls);
        assertEquals(0, flow.activeCellCount());
        assertEquals(200_000, water.lookup().amount(0, 0, 0));
    }

    private static final class RecordingScheduler
            implements ProcessScheduler {
        private int calls;
        private long delayTicks;
        private long processId;

        @Override
        public void scheduleAfter(
                long delayTicks,
                long processId) {
            calls++;
            this.delayTicks = delayTicks;
            this.processId = processId;
        }
    }
}
