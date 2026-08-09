package io.github.evoforge.simulation.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScheduledTaskTest {

        @Test
        void storesTaskData() {
                TaskHandle handle = TaskHandle.of(5);

                HandlerId handlerId = HandlerId.of(2);

                ScheduledTask task = new ScheduledTask(
                                handle,
                                100,
                                handlerId,
                                17);

                assertEquals(
                                handle,
                                task.handle());

                assertEquals(
                                100,
                                task.when());

                assertEquals(
                                handlerId,
                                task.handlerId());

                assertEquals(
                                17,
                                task.processId());
        }

        @Test
        void rejectsNullHandle() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new ScheduledTask(
                                                null,
                                                100,
                                                HandlerId.of(0),
                                                17));
        }

        @Test
        void rejectsNegativeTime() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new ScheduledTask(
                                                TaskHandle.of(0),
                                                -1,
                                                HandlerId.of(0),
                                                17));
        }

        @Test
        void rejectsNullHandlerId() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new ScheduledTask(
                                                TaskHandle.of(0),
                                                100,
                                                null,
                                                17));
        }
}