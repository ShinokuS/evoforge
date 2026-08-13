package io.github.evoforge.visualizer.scenario;

/**
 * Optional presentation-tooling lifecycle for scenarios whose diagnostics,
 * setup or interaction evolve with authoritative simulation ticks.
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

    /** Optional scenario reaction to the ordinary LMB world-cell selection. */
    default void primaryCellAction(int x, int y, int z) {
        // Most scenarios do not need cell interaction.
    }

    /** Optional scenario reaction to RMB on a world cell. */
    default boolean secondaryCellAction(int x, int y, int z) {
        return false;
    }

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
