package io.github.evoforge.visualizer.presentation;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;

/** Procedural presentation binding for cardinal ramps. */
final class RampShapePresentation
        implements ShapePresentation<RampShape> {

    private final ProceduralRampArt art = new ProceduralRampArt();

    @Override
    public TextureRegion terrainRegion(
            RampShape shape,
            int topologyMask,
            int variant,
            boolean solidBody) {

        return art.region(
                shape.riseX(),
                shape.riseY(),
                topologyMask,
                variant);
    }

    @Override
    public boolean genericReliefEdgeAllowed(
            RampShape shape,
            CellFace face) {
        if (shape == null || face == null || face.dz() != 0) {
            throw new IllegalArgumentException(
                    "ramp relief presentation requires a horizontal face");
        }

        // Ramp art owns the whole visible boundary. Low/high contacts are intentionally open,
        // while exposed lateral banks are drawn by the topology-aware ramp tile itself. The
        // generic relief pass must therefore never outline a ramp from either side of the pair.
        return false;
    }

    @Override
    public ShapeDirectionDiagnostic directionDiagnostic(
            RampShape shape) {

        return ShapeDirectionDiagnostic.cardinal(
                shape.riseX(),
                shape.riseY());
    }

    @Override
    public String debugLabel(
            RampShape shape) {

        return "Ramp " + axisLabel(shape.riseX(), shape.riseY());
    }

    @Override
    public void dispose() {
        art.dispose();
    }

    private static String axisLabel(
            int x,
            int y) {

        if (x == 1) return "+X";
        if (x == -1) return "-X";
        if (y == 1) return "+Y";
        if (y == -1) return "-Y";
        throw new IllegalArgumentException(
                "unsupported ramp rise vector " + x + "," + y);
    }
}
