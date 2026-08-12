package io.github.evoforge.visualizer.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import org.junit.jupiter.api.Test;

final class ShapePresentationRegistryTest {

    @Test
    void dispatchesByExactRegisteredShapeType() {
        ShapePresentationRegistry registry = new ShapePresentationRegistry();
        registry.register(FirstShape.class, new NamedPresentation<>("first"));
        registry.register(SecondShape.class, new NamedPresentation<>("second"));

        assertEquals("first", registry.debugLabel(new FirstShape()));
        assertEquals("second", registry.debugLabel(new SecondShape()));
    }

    @Test
    void rejectsDuplicateRegistration() {
        ShapePresentationRegistry registry = new ShapePresentationRegistry();
        registry.register(FirstShape.class, new NamedPresentation<>("first"));

        assertThrows(
                IllegalStateException.class,
                () -> registry.register(
                        FirstShape.class,
                        new NamedPresentation<>("replacement")));
    }

    @Test
    void failsFastWhenShapeHasNoPresentationBinding() {
        ShapePresentationRegistry registry = new ShapePresentationRegistry();

        assertThrows(
                IllegalStateException.class,
                () -> registry.debugLabel(new FirstShape()));
    }

    private static final class FirstShape implements Shape {
        @Override
        public long transitionPorts(int x, int y, int z) {
            return 0L;
        }
    }

    private static final class SecondShape implements Shape {
        @Override
        public long transitionPorts(int x, int y, int z) {
            return 0L;
        }
    }

    private static final class NamedPresentation<S extends Shape>
            implements ShapePresentation<S> {

        private final String name;

        private NamedPresentation(String name) {
            this.name = name;
        }

        @Override
        public TextureRegion terrainRegion(
                S shape,
                int topologyMask,
                int variant,
                boolean solidBody) {

            return null;
        }

        @Override
        public String debugLabel(S shape) {
            return name;
        }
    }
}
