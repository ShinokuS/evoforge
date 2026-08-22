package io.github.evoforge.simulation.kernel.scheduling;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerTest {

        @Test
        void schedulesTask() {
                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                processId -> {
                                });

                Scheduler scheduler = new Scheduler(handlers);

                TaskHandle handle = scheduler.schedule(
                                100,
                                handlerId,
                                17);

                assertEquals(
                                TaskHandle.of(0),
                                handle);

                assertEquals(
                                1,
                                scheduler.size());
        }

        @Test
        void assignsSequentialHandles() {
                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                processId -> {
                                });

                Scheduler scheduler = new Scheduler(handlers);

                TaskHandle first = scheduler.schedule(
                                100,
                                handlerId,
                                1);

                TaskHandle second = scheduler.schedule(
                                100,
                                handlerId,
                                2);

                assertEquals(
                                TaskHandle.of(0),
                                first);

                assertEquals(
                                TaskHandle.of(1),
                                second);
        }

        @Test
        void dispatchesDueTask() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                handled::add);

                Scheduler scheduler = new Scheduler(handlers);

                scheduler.schedule(
                                10,
                                handlerId,
                                42);

                scheduler.dispatchDue(10);

                assertEquals(
                                List.of(42L),
                                handled);

                assertEquals(
                                0,
                                scheduler.size());
        }

        @Test
        void doesNotDispatchFutureTask() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                handled::add);

                Scheduler scheduler = new Scheduler(handlers);

                scheduler.schedule(
                                20,
                                handlerId,
                                42);

                scheduler.dispatchDue(10);

                assertTrue(
                                handled.isEmpty());

                assertEquals(
                                1,
                                scheduler.size());
        }

        @Test
        void dispatchesOverdueTask() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                handled::add);

                Scheduler scheduler = new Scheduler(handlers);

                scheduler.schedule(
                                5,
                                handlerId,
                                42);

                scheduler.dispatchDue(10);

                assertEquals(
                                List.of(42L),
                                handled);
        }

        @Test
        void dispatchesInTimeOrder() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                handled::add);

                Scheduler scheduler = new Scheduler(handlers);

                scheduler.schedule(
                                30,
                                handlerId,
                                3);

                scheduler.schedule(
                                10,
                                handlerId,
                                1);

                scheduler.schedule(
                                20,
                                handlerId,
                                2);

                scheduler.dispatchDue(30);

                assertEquals(
                                List.of(
                                                1L,
                                                2L,
                                                3L),
                                handled);
        }

        @Test
        void dispatchesSameTimeTasksInScheduleOrder() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                handled::add);

                Scheduler scheduler = new Scheduler(handlers);

                scheduler.schedule(
                                10,
                                handlerId,
                                1);

                scheduler.schedule(
                                10,
                                handlerId,
                                2);

                scheduler.schedule(
                                10,
                                handlerId,
                                3);

                scheduler.dispatchDue(10);

                assertEquals(
                                List.of(
                                                1L,
                                                2L,
                                                3L),
                                handled);
        }

        @Test
        void cancelsTask() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                handled::add);

                Scheduler scheduler = new Scheduler(handlers);

                TaskHandle handle = scheduler.schedule(
                                10,
                                handlerId,
                                42);

                assertTrue(
                                scheduler.cancel(handle));

                scheduler.dispatchDue(10);

                assertTrue(
                                handled.isEmpty());

                assertEquals(
                                0,
                                scheduler.size());
        }

        @Test
        void cancellingUnknownTaskReturnsFalse() {
                HandlerRegistry handlers = new HandlerRegistry();

                Scheduler scheduler = new Scheduler(handlers);

                assertFalse(
                                scheduler.cancel(
                                                TaskHandle.of(100)));
        }

        @Test
        void cancellingCompletedTaskReturnsFalse() {
                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                processId -> {
                                });

                Scheduler scheduler = new Scheduler(handlers);

                TaskHandle handle = scheduler.schedule(
                                10,
                                handlerId,
                                42);

                scheduler.dispatchDue(10);

                assertFalse(
                                scheduler.cancel(handle));
        }

        @Test
        void canCancelLaterTaskFromSameBatch() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                Scheduler scheduler = new Scheduler(handlers);

                AtomicReference<TaskHandle> secondHandle = new AtomicReference<>();

                HandlerId handlerId = handlers.register(
                                processId -> {
                                        handled.add(processId);

                                        if (processId == 1) {
                                                scheduler.cancel(
                                                                secondHandle.get());
                                        }
                                });

                scheduler.schedule(
                                10,
                                handlerId,
                                1);

                secondHandle.set(
                                scheduler.schedule(
                                                10,
                                                handlerId,
                                                2));

                scheduler.dispatchDue(10);

                assertEquals(
                                List.of(1L),
                                handled);
        }

        @Test
        void taskScheduledDuringDispatchWaitsForNextDispatch() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                Scheduler scheduler = new Scheduler(handlers);

                HandlerId secondHandler = handlers.register(
                                handled::add);

                HandlerId firstHandler = handlers.register(
                                processId -> {
                                        handled.add(processId);

                                        scheduler.schedule(
                                                        10,
                                                        secondHandler,
                                                        2);
                                });

                scheduler.schedule(
                                10,
                                firstHandler,
                                1);

                scheduler.dispatchDue(10);

                assertEquals(
                                List.of(1L),
                                handled);

                assertEquals(
                                1,
                                scheduler.size());

                scheduler.dispatchDue(10);

                assertEquals(
                                List.of(
                                                1L,
                                                2L),
                                handled);
        }

        @Test
        void rejectsUnknownHandler() {
                HandlerRegistry handlers = new HandlerRegistry();

                Scheduler scheduler = new Scheduler(handlers);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> scheduler.schedule(
                                                10,
                                                HandlerId.of(100),
                                                42));
        }

        @Test
        void rejectsNegativeScheduleTime() {
                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                processId -> {
                                });

                Scheduler scheduler = new Scheduler(handlers);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> scheduler.schedule(
                                                -1,
                                                handlerId,
                                                42));
        }

        @Test
        void rejectsNegativeDispatchTime() {
                HandlerRegistry handlers = new HandlerRegistry();

                Scheduler scheduler = new Scheduler(handlers);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> scheduler.dispatchDue(-1));
        }

        @Test
        void rejectsReentrantDispatch() {
                HandlerRegistry handlers = new HandlerRegistry();

                Scheduler scheduler = new Scheduler(handlers);

                HandlerId handlerId = handlers.register(
                                processId -> assertThrows(
                                                IllegalStateException.class,
                                                () -> scheduler.dispatchDue(10)));

                scheduler.schedule(
                                10,
                                handlerId,
                                1);

                scheduler.dispatchDue(10);
        }

        @Test
        void handlerFailureDoesNotPreventLaterTasksInSameBatch() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                processId -> {
                                        handled.add(
                                                        processId);

                                        if (processId == 2) {
                                                throw new IllegalStateException(
                                                                "boom");
                                        }
                                });

                Scheduler scheduler = new Scheduler(handlers);

                scheduler.schedule(
                                10,
                                handlerId,
                                1);

                scheduler.schedule(
                                10,
                                handlerId,
                                2);

                scheduler.schedule(
                                10,
                                handlerId,
                                3);

                Scheduler.DispatchException exception = assertThrows(
                                Scheduler.DispatchException.class,
                                () -> scheduler.dispatchDue(10));

                assertEquals(
                                List.of(
                                                1L,
                                                2L,
                                                3L),
                                handled);

                assertEquals(
                                1,
                                exception.failureCount());

                assertEquals(
                                0,
                                scheduler.size());
        }

        @Test
        void collectsMultipleHandlerFailures() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                processId -> {
                                        handled.add(
                                                        processId);

                                        if (processId == 2
                                                        || processId == 4) {

                                                throw new IllegalStateException(
                                                                "boom " + processId);
                                        }
                                });

                Scheduler scheduler = new Scheduler(handlers);

                scheduler.schedule(
                                10,
                                handlerId,
                                1);

                scheduler.schedule(
                                10,
                                handlerId,
                                2);

                scheduler.schedule(
                                10,
                                handlerId,
                                3);

                scheduler.schedule(
                                10,
                                handlerId,
                                4);

                Scheduler.DispatchException exception = assertThrows(
                                Scheduler.DispatchException.class,
                                () -> scheduler.dispatchDue(10));

                assertEquals(
                                List.of(
                                                1L,
                                                2L,
                                                3L,
                                                4L),
                                handled);

                assertEquals(
                                2,
                                exception.failureCount());

                assertEquals(
                                1,
                                exception.getSuppressed().length);

                assertEquals(
                                0,
                                scheduler.size());
        }

        @Test
        void failedTaskIsNotRetried() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                processId -> {
                                        handled.add(
                                                        processId);

                                        throw new IllegalStateException(
                                                        "boom");
                                });

                Scheduler scheduler = new Scheduler(handlers);

                scheduler.schedule(
                                10,
                                handlerId,
                                1);

                assertThrows(
                                Scheduler.DispatchException.class,
                                () -> scheduler.dispatchDue(10));

                assertEquals(
                                List.of(1L),
                                handled);

                assertEquals(
                                0,
                                scheduler.size());

                scheduler.dispatchDue(10);

                assertEquals(
                                List.of(1L),
                                handled);
        }

        @Test
        void canDispatchAgainAfterHandlerFailure() {
                List<Long> handled = new ArrayList<>();

                HandlerRegistry handlers = new HandlerRegistry();

                HandlerId handlerId = handlers.register(
                                processId -> {
                                        handled.add(
                                                        processId);

                                        if (processId == 1) {
                                                throw new IllegalStateException(
                                                                "boom");
                                        }
                                });

                Scheduler scheduler = new Scheduler(handlers);

                scheduler.schedule(
                                10,
                                handlerId,
                                1);

                assertThrows(
                                Scheduler.DispatchException.class,
                                () -> scheduler.dispatchDue(10));

                scheduler.schedule(
                                20,
                                handlerId,
                                2);

                scheduler.dispatchDue(20);

                assertEquals(
                                List.of(
                                                1L,
                                                2L),
                                handled);
        }
}