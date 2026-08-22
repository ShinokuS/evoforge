package io.github.evoforge.visualizer.scenario.agent;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.agents.decision.AgentIntentTrace;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioController;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Presentation-only live summary for the integrated living-Cow acceptance scenario. */
final class LivingCowController implements ScenarioController {
    private final SimulationRuntime runtime;
    private final List<ObjectId> cows;
    private final NeedId hunger;
    private final NeedId thirst;
    private final Map<String, String> names;
    private ScenarioDiagnostics diagnostics = ScenarioDiagnostics.NONE;

    LivingCowController(
            SimulationRuntime runtime,
            List<ObjectId> cows,
            NeedId hunger,
            NeedId thirst,
            Map<ObjectId, String> names) {
        if (runtime == null || cows == null || cows.isEmpty() || hunger == null || thirst == null || names == null) {
            throw new IllegalArgumentException("living Cow diagnostics values must not be null or empty");
        }
        this.runtime = runtime;
        this.cows = List.copyOf(cows);
        this.hunger = hunger;
        this.thirst = thirst;
        Map<String, String> targetNames = new LinkedHashMap<>();
        for (Map.Entry<ObjectId, String> entry : names.entrySet()) {
            targetNames.put(objectTargetKey(entry.getKey()), entry.getValue());
        }
        this.names = Map.copyOf(targetNames);
        update(runtime.time().tick());
    }

    @Override
    public void update(long tick) {
        StringBuilder summary = new StringBuilder();
        for (int index = 0; index < cows.size(); index++) {
            if (index > 0) summary.append(" || ");
            ObjectId cow = cows.get(index);
            long hungerLevel = runtime.view().needs().level(cow, hunger);
            long thirstLevel = runtime.view().needs().level(cow, thirst);
            AgentIntentTrace intent = runtime.view().agents().currentIntent(cow);
            String activity = intent == null ? "idle / deciding" : intent.phase().name();
            String target = targetLabel(intent == null ? null : intent.targetKey());
            summary.append(names.getOrDefault(objectTargetKey(cow), "Cow " + (index + 1)))
                    .append(" H ").append(hungerLevel)
                    .append(" T ").append(thirstLevel)
                    .append(" | ").append(activity)
                    .append(" -> ").append(target);
        }
        summary.append(" | click a Cow/plant for authoritative inspector");
        diagnostics = new ScenarioDiagnostics(new ScenarioCellMarker[0], summary.toString());
    }

    @Override
    public ScenarioDiagnostics diagnostics() {
        return diagnostics;
    }

    private String targetLabel(String targetKey) {
        if (targetKey == null) return "none";
        String named = names.get(targetKey);
        if (named != null) return named;
        if (targetKey.startsWith("liquid:")) {
            int at = targetKey.indexOf('@');
            int hash = targetKey.lastIndexOf('#');
            if (at >= 0 && hash > at) return "Water " + targetKey.substring(at + 1, hash);
            return "Water";
        }
        return targetKey;
    }

    private static String objectTargetKey(ObjectId objectId) {
        return "object:" + objectId.asLong();
    }
}
