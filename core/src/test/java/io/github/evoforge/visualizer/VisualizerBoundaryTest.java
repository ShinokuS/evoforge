package io.github.evoforge.visualizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.time.SimulationStepper;
import io.github.evoforge.simulation.time.SimulationTime;
import org.junit.jupiter.api.Test;

final class VisualizerBoundaryTest {

    @Test
    void rendererConstructorExposesOnlyReadAndTimeCapabilities() {
        Class<?>[] parameterTypes =
                ZLevelVisualizer.class
                        .getConstructors()[0]
                        .getParameterTypes();

        assertArrayEquals(
                new Class<?>[] {
                    SimulationView.class,
                    SimulationTime.class,
                    SimulationStepper.class
                },
                parameterTypes);
    }
}
