package io.github.evoforge.simulation.world.diagnostics.warmup;

import java.util.ArrayList;
import java.util.List;

import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnostics;
import io.github.evoforge.simulation.world.diagnostics.GeneratedWorldDiagnosticsProbe;

/**
 * Advances one generated production runtime to explicit absolute diagnostic checkpoints.
 *
 * <p>Warmup owns no simulation law and has no concept of equilibrium or viability. It advances
 * the ordinary {@code SimulationStepper} exactly as requested and observes the result through the
 * existing generated-world diagnostics boundary.</p>
 */
public final class GeneratedWorldWarmup {

    private final GeneratedWorldDiagnosticsProbe diagnostics;

    public GeneratedWorldWarmup() {
        this(new GeneratedWorldDiagnosticsProbe());
    }

    public GeneratedWorldWarmup(
            GeneratedWorldDiagnosticsProbe diagnostics) {
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics must not be null");
        }
        this.diagnostics = diagnostics;
    }

    /**
     * Returns immutable snapshots captured at each strictly increasing absolute checkpoint tick.
     * The first checkpoint may equal the runtime's current tick.
     */
    public List<GeneratedWorldDiagnostics> run(
            GeneratedWorldRuntime world,
            long... checkpointTicks) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (checkpointTicks == null || checkpointTicks.length == 0) {
            throw new IllegalArgumentException(
                    "at least one warmup checkpoint is required");
        }

        long current = world.runtime().time().tick();
        long previous = -1L;
        List<GeneratedWorldDiagnostics> snapshots =
                new ArrayList<>(checkpointTicks.length);

        for (int index = 0; index < checkpointTicks.length; index++) {
            long target = checkpointTicks[index];
            if (target < 0L) {
                throw new IllegalArgumentException(
                        "warmup checkpoint must not be negative: " + target);
            }
            if (index > 0 && target <= previous) {
                throw new IllegalArgumentException(
                        "warmup checkpoints must be strictly increasing");
            }
            if (target < current) {
                throw new IllegalArgumentException(
                        "warmup checkpoint is before current runtime tick: " + target);
            }

            while (world.runtime().time().tick() < target) {
                world.runtime().stepper().advance();
            }
            snapshots.add(diagnostics.snapshot(
                    world.atlas(),
                    world.runtime()));
            current = target;
            previous = target;
        }

        return List.copyOf(snapshots);
    }
}
