package io.github.evoforge.visualizer.scenario.agent;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.agents.decision.AgentCandidateTrace;
import io.github.evoforge.simulation.agents.decision.AgentDecisionTrace;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioController;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;
import java.util.ArrayList;
import java.util.List;

/** Presentation-only snapshot of the authoritative autonomous decision trace. */
final class CowForagingController implements ScenarioController {

    private final SimulationRuntime runtime;
    private final ObjectId cow;
    private final ObjectId grass;
    private final ObjectId hay;
    private final NeedId hunger;
    private AgentDecisionTrace lastSelectedDecision;

    CowForagingController(
            SimulationRuntime runtime,
            ObjectId cow,
            ObjectId grass,
            ObjectId hay,
            NeedId hunger) {
        this.runtime = runtime;
        this.cow = cow;
        this.grass = grass;
        this.hay = hay;
        this.hunger = hunger;
    }

    @Override
    public void update(long tick) {
        AgentDecisionTrace trace = runtime.view().agents().lastDecision(cow);
        if (trace != null && trace.selected() != null) {
            lastSelectedDecision = trace;
        }
    }

    @Override
    public ScenarioDiagnostics diagnostics() {
        AgentDecisionTrace current = runtime.view().agents().lastDecision(cow);
        AgentDecisionTrace display = current != null && current.selected() != null
                ? current
                : lastSelectedDecision;

        List<ScenarioCellMarker> markers = new ArrayList<>();
        if (display != null) {
            for (AgentCandidateTrace candidate : display.candidates()) {
                ScenarioCellMarkerStyle style = candidate.equals(display.selected())
                        ? ScenarioCellMarkerStyle.GOAL
                        : ScenarioCellMarkerStyle.WARNING;
                markers.add(new ScenarioCellMarker(
                        candidate.x(), candidate.y(), candidate.z(), style));
            }
        }

        long level = runtime.view().needs().level(cow, hunger);
        long max = runtime.view().needs().maxLevel(cow, hunger);
        String target = runtime.view().agents().currentTargetKey(cow);
        String summary = "hunger=" + level + "/" + max
                + " | target=" + label(target)
                + decisionSummary(display);
        return new ScenarioDiagnostics(
                markers.toArray(ScenarioCellMarker[]::new),
                summary);
    }

    private String decisionSummary(AgentDecisionTrace trace) {
        if (trace == null || trace.selected() == null) {
            return " | decision=waiting for first think";
        }
        AgentCandidateTrace selected = trace.selected();
        return " | decisionTick=" + trace.tick()
                + " | candidates=" + trace.candidates().size()
                + " | winner=" + label(selected.targetKey())
                + " | benefit=" + selected.expectedBenefit()
                + " | distance=" + selected.distance()
                + " | utility=" + selected.utility();
    }

    private String label(String targetKey) {
        if (targetKey == null) return "none";
        if (targetKey.equals(objectTargetKey(grass))) return "grass";
        if (targetKey.equals(objectTargetKey(hay))) return "hay";
        if (targetKey.equals(objectTargetKey(cow))) return "cow";
        return targetKey;
    }

    private static String objectTargetKey(ObjectId objectId) {
        return "object:" + objectId.asLong();
    }
}
