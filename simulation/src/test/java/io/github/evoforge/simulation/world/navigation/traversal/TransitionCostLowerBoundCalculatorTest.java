package io.github.evoforge.simulation.world.navigation.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import org.junit.jupiter.api.Test;
import io.github.evoforge.simulation.world.navigation.traversal.MaterialTraversalDefinitions;

final class TransitionCostLowerBoundCalculatorTest {

    @Test
    void derivesNeutralFloorFromTerrainAndShapeBounds() {
        MaterialTraversalDefinitions definitions =
                new MaterialTraversalDefinitions();
        definitions.put(
                MaterialDefinitionId.of(0),
                SurfaceTraversalCost.of(1000));
        definitions.put(
                MaterialDefinitionId.of(1),
                SurfaceTraversalCost.of(1800));

        TransitionCostLowerBoundCalculator bounds =
                new TransitionCostLowerBoundCalculator(
                        definitions,
                        () -> 1000);

        assertEquals(1000, bounds.minimumEdgeCostUnits());
    }

    @Test
    void respectsSubNeutralTerrainAndShapeFactorsWithoutOverestimating() {
        MaterialTraversalDefinitions definitions =
                new MaterialTraversalDefinitions();
        definitions.put(
                MaterialDefinitionId.of(0),
                SurfaceTraversalCost.of(750));

        TransitionCostLowerBoundCalculator bounds =
                new TransitionCostLowerBoundCalculator(
                        definitions,
                        () -> 400);

        assertEquals(300, bounds.minimumEdgeCostUnits());
    }

    @Test
    void remainsPositiveWhenFixedPointProductRoundsBelowOne() {
        MaterialTraversalDefinitions definitions =
                new MaterialTraversalDefinitions();
        definitions.put(
                MaterialDefinitionId.of(0),
                SurfaceTraversalCost.of(1));

        TransitionCostLowerBoundCalculator bounds =
                new TransitionCostLowerBoundCalculator(
                        definitions,
                        () -> 1);

        assertEquals(1, bounds.minimumEdgeCostUnits());
    }
}
