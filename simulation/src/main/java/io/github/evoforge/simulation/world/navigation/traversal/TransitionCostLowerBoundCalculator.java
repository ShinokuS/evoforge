package io.github.evoforge.simulation.world.navigation.traversal;

import io.github.evoforge.simulation.world.geometry.ShapeTraversalFactor;
import io.github.evoforge.simulation.world.geometry.ShapeTraversalLowerBoundLookup;
import io.github.evoforge.simulation.world.navigation.traversal.MaterialTraversalDefinitions;

/** Derives an admissible global edge-cost floor from current traversal facts. */
public final class TransitionCostLowerBoundCalculator
        implements TransitionCostLowerBoundLookup {

    private final MaterialTraversalDefinitions definitions;
    private final ShapeTraversalLowerBoundLookup shapeBounds;

    public TransitionCostLowerBoundCalculator(
            MaterialTraversalDefinitions definitions,
            ShapeTraversalLowerBoundLookup shapeBounds) {

        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }
        if (shapeBounds == null) {
            throw new IllegalArgumentException(
                    "shapeBounds must not be null");
        }

        this.definitions = definitions;
        this.shapeBounds = shapeBounds;
    }

    @Override
    public long minimumEdgeCostUnits() {
        if (!definitions.hasAny()) {
            return 1L;
        }

        long minimumSurface = definitions.minimumCostUnits();
        int minimumFactor = ShapeTraversalFactor.requirePositive(
                shapeBounds.minimumTraversalFactor());

        long weighted = Math.multiplyExact(
                minimumSurface,
                minimumFactor);
        long quotient = weighted / ShapeTraversalFactor.SCALE;
        long remainder = weighted % ShapeTraversalFactor.SCALE;

        if (remainder * 2 >= ShapeTraversalFactor.SCALE) {
            quotient = Math.addExact(quotient, 1L);
        }

        return Math.max(1L, quotient);
    }
}
