package io.github.evoforge.visualizer.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ContinuumInfiniteTimeInspectorModelTest {

    @Test
    void youngAndAncientPresetsKeepSameCurrentWorkingSet() {
        ContinuumInfiniteTimeInspectorModel model = new ContinuumInfiniteTimeInspectorModel();
        assertEquals(6, model.sleepingProcesses());
        assertEquals(6, model.queuedWakeEntries());

        model.setAncientWorld();

        assertEquals(1_000_000L, model.now().era());
        assertEquals(6, model.sleepingProcesses());
        assertEquals(6, model.queuedWakeEntries());
        assertEquals(0, model.schedulerChurnQueueEntries());
        assertEquals(1, model.schedulerChurnHandleSlots());
    }

    @Test
    void hugeJumpHandlesOnlyDueProcessesOnce() {
        ContinuumInfiniteTimeInspectorModel model = new ContinuumInfiniteTimeInspectorModel();

        model.jumpHugeInterval();

        assertEquals(ContinuumInfiniteTimeInspectorModel.HUGE_JUMP_TICKS, model.lastJumpTicks());
        assertEquals(6, model.lastWakeOperations());
        assertEquals(6L, model.totalWakeOperations());
        assertEquals(0, model.sleepingProcesses());
        assertEquals(0, model.queuedWakeEntries());
        assertTrue(model.processRows().stream().noneMatch(ContinuumInfiniteTimeInspectorModel.ProcessRow::sleeping));
    }

    @Test
    void millionChangesBecomeCompactCurrentState() {
        ContinuumInfiniteTimeInspectorModel model = new ContinuumInfiniteTimeInspectorModel();

        model.compactMillionChanges();

        assertEquals(1_000_000L, model.compactedCurrentState());
        assertTrue(model.retainedHistoryEntries() < ContinuumInfiniteTimeInspectorModel.HISTORY_TAIL_LIMIT);
        assertEquals(15_625L, model.compactions());
    }
}
