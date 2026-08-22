package io.github.evoforge.visualizer.scenario.agent;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.agents.search.AgentSearchTrace;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.space.orientation.FacingDirection;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioController;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;

/** Presentation-only summary of authoritative Vision/search state. */
final class CowVisualSearchController implements ScenarioController {
    private final SimulationRuntime runtime;
    private final ObjectId cow;
    private final ObjectId grass;
    private final NeedId hunger;

    CowVisualSearchController(SimulationRuntime runtime, ObjectId cow, ObjectId grass, NeedId hunger) {
        this.runtime = runtime;
        this.cow = cow;
        this.grass = grass;
        this.hunger = hunger;
    }

    @Override
    public void update(long tick) {
        // Diagnostics are read live from authoritative simulation state.
    }

    @Override
    public ScenarioDiagnostics diagnostics() {
        long level = runtime.view().needs().level(cow, hunger);
        boolean visible = runtime.view().vision().snapshot(cow).isObjectVisible(grass);
        String target = runtime.view().agents().currentTargetKey(cow);
        AgentSearchTrace current = runtime.view().searches().currentSearch(cow);
        AgentSearchTrace last = runtime.view().searches().lastSearch(cow);
        AgentSearchTrace display = current != null ? current : last;
        FacingDirection facing = runtime.view().orientations().facing(cow);
        String search = display == null
                ? "none"
                : display.status() + ":" + display.motivation() + ":views=" + display.headingsObserved();
        boolean targetingGrass = objectTargetKey(grass).equals(target);
        String summary = "hunger=" + level
                + " | facing=" + facing.x() + "," + facing.y()
                + " | grassVisible=" + visible
                + " | search=" + search
                + " | target=" + (targetingGrass ? "grass" : target == null ? "none" : target);
        ScenarioCellMarker[] markers = targetingGrass
                ? new ScenarioCellMarker[] { new ScenarioCellMarker(
                        runtime.view().positions().x(grass),
                        runtime.view().positions().y(grass),
                        runtime.view().positions().z(grass),
                        ScenarioCellMarkerStyle.GOAL) }
                : new ScenarioCellMarker[0];
        return new ScenarioDiagnostics(markers, summary);
    }

    private static String objectTargetKey(ObjectId objectId) {
        return "object:" + objectId.asLong();
    }
}
