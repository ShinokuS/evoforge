package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioController;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;

/** Small bounded diagnostics for focused Water acceptance worlds. */
public final class WaterScenarioDiagnostics implements ScenarioController {

    private final SimulationRuntime runtime;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;
    private ScenarioDiagnostics diagnostics = ScenarioDiagnostics.NONE;
    private long lastTick = Long.MIN_VALUE;

    public WaterScenarioDiagnostics(
            SimulationRuntime runtime,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {

        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("diagnostic bounds must be ordered");
        }
        this.runtime = runtime;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        update(runtime.time().tick());
    }

    @Override
    public void update(long tick) {
        if (tick == lastTick) return;
        lastTick = tick;

        long totalWater = 0L;
        long retainedWater = 0L;
        int wetCells = 0;
        StringBuilder layers = new StringBuilder();

        for (int z = minZ; z <= maxZ; z++) {
            long layerWater = 0L;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    int water = runtime.view().water().amount(x, y, z);
                    int retained = runtime.view().soilLiquids().amountOf(
                            WaterSystem.TYPE,
                            x,
                            y,
                            z);
                    layerWater += water;
                    totalWater += water;
                    retainedWater += retained;
                    if (water > 0) wetCells++;
                }
            }
            if (layerWater > 0) {
                if (!layers.isEmpty()) layers.append(" · ");
                layers.append('z').append(z).append('=').append(layerWater);
            }
        }

        String summary = "Water=" + totalWater
                + " · wetCells=" + wetCells
                + " · retainedWater=" + retainedWater
                + (layers.isEmpty() ? "" : " · " + layers);
        diagnostics = new ScenarioDiagnostics(
                new ScenarioCellMarker[0],
                summary);
    }

    @Override
    public ScenarioDiagnostics diagnostics() {
        return diagnostics;
    }
}
