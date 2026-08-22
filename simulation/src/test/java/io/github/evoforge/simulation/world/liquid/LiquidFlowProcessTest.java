package io.github.evoforge.simulation.world.liquid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.kernel.scheduling.ProcessScheduler;
import io.github.evoforge.simulation.world.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.geometry.TransitionPorts;

final class LiquidFlowProcessTest {

    private static final LiquidTypeId LIQUID = LiquidTypeId.of("test-liquid");

    @Test
    void coalescesWakeupsAndStopsSchedulingAtDormancy() {
        Shape isolatedFreeCell = new Shape() {
            @Override
            public int solidVolume() {
                return CellVolume.EMPTY;
            }

            @Override
            public long transitionPorts(int relativeX, int relativeY, int relativeZ) {
                return TransitionPorts.NONE;
            }
        };

        LiquidSystem liquids = new LiquidSystem(
                new SparseLiquidStorage(),
                (x, y, z) -> isolatedFreeCell);
        LiquidFlowSystem flow = new LiquidFlowSystem(
                liquids,
                (x, y, z) -> isolatedFreeCell,
                type -> LiquidTransportProperties.reference());
        LiquidFlowProcess process = new LiquidFlowProcess(flow);
        RecordingScheduler scheduler = new RecordingScheduler();
        process.bindScheduler(scheduler);

        liquids.addAtMost(LIQUID, 0, 0, 0, 200_000);
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
        assertEquals(200_000, liquids.lookup().amountOf(LIQUID, 0, 0, 0));
    }

    private static final class RecordingScheduler implements ProcessScheduler {
        private int calls;
        private long delayTicks;
        private long processId;

        @Override
        public void scheduleAfter(long delayTicks, long processId) {
            calls++;
            this.delayTicks = delayTicks;
            this.processId = processId;
        }
    }
}
