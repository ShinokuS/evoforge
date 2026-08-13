package io.github.evoforge.visualizer.scenario;

/**
 * Optional presentation-tooling lifecycle for scenarios whose diagnostics or
 * setup intentionally evolve with authoritative simulation ticks.
 *
 * <p>This is not a simulation system. It exists only in the development
 * visualizer and must drive behavior from simulation time, never wall-clock
 * time.</p>
 */
public interface ScenarioController {

    ScenarioController NONE = fixed(ScenarioDiagnostics.NONE);

    /** Observe the current authoritative simulation tick and advance scenario tooling. */
    void update(long tick);

    /** Current presentation-only diagnostics for this scenario. */
    ScenarioDiagnostics diagnostics();

    static ScenarioController fixed(ScenarioDiagnostics diagnostics) {
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics must not be null");
        }
        return new ScenarioController() {
            @Override
            public void update(long tick) {
                // Fixed scenarios have no tick-driven tooling.
            }

            @Override
            public ScenarioDiagnostics diagnostics() {
                return diagnostics;
            }
        };
    }
}
