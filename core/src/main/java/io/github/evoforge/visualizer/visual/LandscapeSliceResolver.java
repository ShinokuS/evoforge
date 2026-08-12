package io.github.evoforge.visualizer.visual;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/**
 * Resolves what one XY cell means in a selected horizontal standing-Z slice.
 *
 * <p>The presentation contract is intentionally asymmetric around the selected
 * plane: terrain anchored at {@code selectedZ} is solid body intersecting the
 * slice; terrain at {@code selectedZ - 1} is the current walkable/support
 * surface; only when both are absent may a lower surface be visible through an
 * open vertical column.</p>
 */
public final class LandscapeSliceResolver {

    public enum Kind {
        SOLID_BODY,
        CURRENT_SURFACE,
        LOWER_SURFACE,
        EMPTY
    }

    public record Cell(
            Kind kind,
            int terrainZ,
            int lowerDepth,
            Shape shape) {

        public Cell {
            if (kind == null) {
                throw new IllegalArgumentException("kind must not be null");
            }
            if (kind == Kind.LOWER_SURFACE && lowerDepth <= 0) {
                throw new IllegalArgumentException(
                        "lower surface depth must be positive");
            }
            if (kind != Kind.LOWER_SURFACE && lowerDepth != 0) {
                throw new IllegalArgumentException(
                        "non-lower slice cell must have depth zero");
            }
        }

        public static Cell empty() {
            return new Cell(Kind.EMPTY, Integer.MIN_VALUE, 0, null);
        }
    }

    private final SimulationView view;

    public LandscapeSliceResolver(
            SimulationView view) {

        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        this.view = view;
    }

    public Cell resolve(
            int x,
            int y,
            int selectedStandingZ,
            int maxLowerDepth) {

        if (maxLowerDepth < 0) {
            throw new IllegalArgumentException(
                    "maxLowerDepth must not be negative");
        }

        if (view.terrain().contains(x, y, selectedStandingZ)) {
            return cell(
                    Kind.SOLID_BODY,
                    x,
                    y,
                    selectedStandingZ,
                    0);
        }

        int supportTerrainZ = selectedStandingZ - 1;
        if (view.terrain().contains(x, y, supportTerrainZ)) {
            return cell(
                    Kind.CURRENT_SURFACE,
                    x,
                    y,
                    supportTerrainZ,
                    0);
        }

        for (int depth = 1; depth <= maxLowerDepth; depth++) {
            int terrainZ = selectedStandingZ - depth - 1;
            if (view.terrain().contains(x, y, terrainZ)) {
                return cell(
                        Kind.LOWER_SURFACE,
                        x,
                        y,
                        terrainZ,
                        depth);
            }
        }

        return Cell.empty();
    }

    private Cell cell(
            Kind kind,
            int x,
            int y,
            int terrainZ,
            int lowerDepth) {

        return new Cell(
                kind,
                terrainZ,
                lowerDepth,
                view.geometry().find(x, y, terrainZ));
    }
}
