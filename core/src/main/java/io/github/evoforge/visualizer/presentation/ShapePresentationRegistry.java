package io.github.evoforge.visualizer.presentation;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import java.util.HashMap;
import java.util.Map;

/** Exact-type presentation dispatch for simulation Shapes. */
public final class ShapePresentationRegistry implements Disposable {

    private final Map<Class<? extends Shape>, ShapePresentation<?>> bindings =
            new HashMap<>();

    public <S extends Shape> void register(
            Class<S> shapeType,
            ShapePresentation<S> presentation) {

        if (shapeType == null) {
            throw new IllegalArgumentException("shapeType must not be null");
        }
        if (presentation == null) {
            throw new IllegalArgumentException("presentation must not be null");
        }
        if (bindings.putIfAbsent(shapeType, presentation) != null) {
            throw new IllegalStateException(
                    "presentation already registered for Shape type "
                            + shapeType.getName());
        }
    }

    public TextureRegion terrainRegion(
            Shape shape,
            int topologyMask,
            int variant,
            boolean solidBody) {

        return binding(shape).terrainRegion(
                shape,
                topologyMask,
                variant,
                solidBody);
    }

    public ShapeDirectionDiagnostic directionDiagnostic(Shape shape) {
        return shape == null
                ? ShapeDirectionDiagnostic.NONE
                : binding(shape).directionDiagnostic(shape);
    }

    public String debugLabel(Shape shape) {
        return shape == null ? "none" : binding(shape).debugLabel(shape);
    }

    @Override
    public void dispose() {
        for (ShapePresentation<?> presentation : bindings.values()) {
            presentation.dispose();
        }
        bindings.clear();
    }

    @SuppressWarnings("unchecked")
    private <S extends Shape> ShapePresentation<S> binding(S shape) {
        if (shape == null) {
            throw new IllegalArgumentException("shape must not be null");
        }
        ShapePresentation<?> presentation = bindings.get(shape.getClass());
        if (presentation == null) {
            throw new IllegalStateException(
                    "no presentation registered for Shape type "
                            + shape.getClass().getName());
        }
        return (ShapePresentation<S>) presentation;
    }
}
