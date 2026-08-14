package io.github.evoforge.visualizer.scenario.agent;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.search.AgentSearchTrace;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
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
    public ScenarioDiagnostics diagnostics() {
        long level = runtime.view().needs().level(cow, hunger);
        boolean visible = runtime.view().vision().snapshot(cow).isObjectVisible(grass);
        ObjectId target = runtime.view().agents().currentTarget(cow);
        AgentSearchTrace current = runtime.view().searches().currentSearch(cow);
        AgentSearchTrace last = runtime.view().searches().lastSearch(cow);
        AgentSearchTrace display = current != null ? current : last;
        FacingDirection facing = runtime.view().orientations().facing(cow);
        String search = display == null
                ? "none"
                : display.status() + ":" + display.motivation() + ":views=" + display.headingsObserved();
        String summary = "hunger=" + level
                + " | facing=" + facing.x() + "," + facing.y()
                + " | grassVisible=" + visible
                + " | search=" + search
                + " | target=" + (target == null ? "none" : "grass");
        ScenarioCellMarker[] markers = target != null && target.equals(grass)
                ? new ScenarioCellMarker[] { new ScenarioCellMarker(
                        runtime.view().transforms().x(grass),
                        runtime.view().transforms().y(grass),
                        runtime.view().transforms().z(grass),
                        ScenarioCellMarkerStyle.GOAL) }
                : new ScenarioCellMarker[0];
        return new ScenarioDiagnostics(markers, summary);
    }
}
