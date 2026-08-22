package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.kernel.command.Command;
import io.github.evoforge.simulation.kernel.command.CommandResult;
import io.github.evoforge.simulation.kernel.command.SynchronousCommandGateway;
import io.github.evoforge.simulation.kernel.scheduling.SimulationStepper;
import io.github.evoforge.simulation.kernel.time.SimulationTime;

/**
 * Started simulation runtime. Setup mutation is intentionally absent here.
 */
public final class SimulationRuntime {

    private final SynchronousCommandGateway commands;
    private final SimulationTime time;
    private final SimulationStepper stepper;
    private final SimulationView view;

    SimulationRuntime(
            SynchronousCommandGateway commands,
            SimulationTime time,
            SimulationStepper stepper,
            SimulationView view) {

        if (commands == null) {
            throw new IllegalArgumentException(
                    "commands must not be null");
        }
        if (time == null) {
            throw new IllegalArgumentException(
                    "time must not be null");
        }
        if (stepper == null) {
            throw new IllegalArgumentException(
                    "stepper must not be null");
        }
        if (view == null) {
            throw new IllegalArgumentException(
                    "view must not be null");
        }

        this.commands = commands;
        this.time = time;
        this.stepper = stepper;
        this.view = view;
    }

    public <R extends CommandResult> R submit(
            Command<R> command) {
        return commands.submit(command);
    }

    public SimulationTime time() {
        return time;
    }

    public SimulationStepper stepper() {
        return stepper;
    }

    public SimulationView view() {
        return view;
    }
}
