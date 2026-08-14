package io.github.evoforge.visualizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.time.SimulationStepper;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class VisualizerBoundaryTest {

    @Test
    void rendererKeepsMinimalReadAndTimeConstructor() {
        boolean found = Arrays.stream(ZLevelVisualizer.class.getConstructors())
                .map(Constructor::getParameterTypes)
                .anyMatch(parameters -> Arrays.equals(
                        parameters,
                        new Class<?>[] {
                            SimulationView.class,
                            SimulationTime.class,
                            SimulationStepper.class
                        }));

        assertTrue(found, "minimal read/time visualizer constructor is missing");
    }

    @Test
    void rendererConstructorsDoNotExposeSimulationMutationCapabilities() {
        Class<?>[] allowed = {
            SimulationView.class,
            SimulationTime.class,
            SimulationStepper.class,
            ObjectPresentationBindings.class
        };

        for (Constructor<?> constructor : ZLevelVisualizer.class.getConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                assertTrue(
                        Arrays.asList(allowed).contains(parameter),
                        "unexpected visualizer constructor dependency: " + parameter.getName());
            }
        }
    }
}
