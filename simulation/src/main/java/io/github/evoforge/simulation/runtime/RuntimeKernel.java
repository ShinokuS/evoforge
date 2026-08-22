package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.kernel.scheduling.HandlerRegistry;
import io.github.evoforge.simulation.kernel.scheduling.Scheduler;
import io.github.evoforge.simulation.kernel.time.SimulationClock;
import io.github.evoforge.simulation.kernel.scheduling.SimulationStepper;

/** Shared deterministic scheduler/clock kernel used while assembling one runtime. */
final class RuntimeKernel {
    final HandlerRegistry handlers = new HandlerRegistry();
    final Scheduler scheduler = new Scheduler(handlers);
    final SimulationClock clock = new SimulationClock();
    final SimulationStepper stepper = new SimulationStepper(clock, scheduler);
}
