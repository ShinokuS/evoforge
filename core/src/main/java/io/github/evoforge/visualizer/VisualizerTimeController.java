package io.github.evoforge.visualizer;

import io.github.evoforge.simulation.time.SimulationStepper;

/**
 * Presentation-owned mapping from real frame time to production simulation ticks.
 */
public final class VisualizerTimeController {

    private final SimulationStepper stepper;
    private final float secondsPerTick;

    private boolean running;
    private float accumulatedSeconds;

    public VisualizerTimeController(
            SimulationStepper stepper,
            float secondsPerTick) {

        if (stepper == null) {
            throw new IllegalArgumentException(
                    "stepper must not be null");
        }
        if (!Float.isFinite(secondsPerTick)
                || secondsPerTick <= 0f) {
            throw new IllegalArgumentException(
                    "secondsPerTick must be finite and > 0");
        }

        this.stepper = stepper;
        this.secondsPerTick = secondsPerTick;
    }

    public boolean running() {
        return running;
    }

    public void toggleRunning() {
        setRunning(!running);
    }

    public void setRunning(
            boolean running) {

        if (this.running == running) {
            return;
        }

        this.running = running;
        accumulatedSeconds = 0f;
    }

    public void stepOnce() {
        if (running) {
            throw new IllegalStateException(
                    "single-step requires paused visualizer");
        }

        stepper.advance();
    }

    public int update(
            float deltaSeconds) {

        if (!Float.isFinite(deltaSeconds)
                || deltaSeconds < 0f) {
            throw new IllegalArgumentException(
                    "deltaSeconds must be finite and >= 0");
        }

        if (!running) {
            return 0;
        }

        accumulatedSeconds += deltaSeconds;

        int advancedTicks = 0;
        while (accumulatedSeconds >= secondsPerTick) {
            stepper.advance();
            accumulatedSeconds -= secondsPerTick;
            advancedTicks++;
        }

        return advancedTicks;
    }
}
