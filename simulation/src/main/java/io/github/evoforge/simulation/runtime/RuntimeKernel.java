package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.time.HandlerRegistry;
import io.github.evoforge.simulation.time.Scheduler;
import io.github.evoforge.simulation.time.SimulationClock;
import io.github.evoforge.simulation.time.SimulationStepper;

/** Shared deterministic scheduler/clock kernel used while assembling one runtime. */
final class RuntimeKernel {
    final HandlerRegistry handlers = new HandlerRegistry();
    final Scheduler scheduler = new Scheduler(handlers);
    final SimulationClock clock = new SimulationClock();
    final SimulationStepper stepper = new SimulationStepper(clock, scheduler);
}
