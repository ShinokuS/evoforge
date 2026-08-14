package io.github.evoforge.visualizer.scenario.agent;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentTrace;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioController;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;
import java.util.Map;

/** Presentation-only live summary for the integrated living-Cow acceptance scenario. */
final class LivingCowController implements ScenarioController {
    private final SimulationRuntime runtime;
    private final ObjectId cowId;
    private final NeedId hunger;
    private final Map<ObjectId, String> names;
    private ScenarioDiagnostics diagnostics = ScenarioDiagnostics.NONE;

    LivingCowController(
            SimulationRuntime runtime,
            ObjectId cowId,
            NeedId hunger,
            Map<ObjectId, String> names) {
        this.runtime = runtime;
        this.cowId = cowId;
        this.hunger = hunger;
        this.names = Map.copyOf(names);
        update(runtime.time().tick());
    }

    @Override
    public void update(long tick) {
        long hungerLevel = runtime.view().needs().level(cowId, hunger);
        long hungerMax = runtime.view().needs().maxLevel(cowId, hunger);
        AgentIntentTrace intent = runtime.view().agents().currentIntent(cowId);
        String activity = intent == null ? "thinking / idle" : intent.phase().name();
        String target = intent == null || intent.targetId() == null
                ? "none"
                : names.getOrDefault(intent.targetId(), intent.targetId().toString());
        diagnostics = new ScenarioDiagnostics(
                new ScenarioCellMarker[0],
                "Hunger " + hungerLevel + "/" + hungerMax
                        + " | " + activity
                        + " | target " + target
                        + " | click Cow/plant for authoritative inspector");
    }

    @Override
    public ScenarioDiagnostics diagnostics() {
        return diagnostics;
    }
}
