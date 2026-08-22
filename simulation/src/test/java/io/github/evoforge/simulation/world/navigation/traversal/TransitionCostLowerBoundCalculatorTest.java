package io.github.evoforge.simulation.world.navigation.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import org.junit.jupiter.api.Test;
import io.github.evoforge.simulation.world.mechanics.traversal.LandscapeTraversalDefinitions;

final class TransitionCostLowerBoundCalculatorTest {

    @Test
    void derivesNeutralFloorFromTerrainAndShapeBounds() {
        LandscapeTraversalDefinitions definitions =
                new LandscapeTraversalDefinitions();
        definitions.put(
                LandscapeDefinitionId.of(0),
                SurfaceTraversalCost.of(1000));
        definitions.put(
                LandscapeDefinitionId.of(1),
                SurfaceTraversalCost.of(1800));

        TransitionCostLowerBoundCalculator bounds =
                new TransitionCostLowerBoundCalculator(
                        definitions,
                        () -> 1000);

        assertEquals(1000, bounds.minimumEdgeCostUnits());
    }

    @Test
    void respectsSubNeutralTerrainAndShapeFactorsWithoutOverestimating() {
        LandscapeTraversalDefinitions definitions =
                new LandscapeTraversalDefinitions();
        definitions.put(
                LandscapeDefinitionId.of(0),
                SurfaceTraversalCost.of(750));

        TransitionCostLowerBoundCalculator bounds =
                new TransitionCostLowerBoundCalculator(
                        definitions,
                        () -> 400);

        assertEquals(300, bounds.minimumEdgeCostUnits());
    }

    @Test
    void remainsPositiveWhenFixedPointProductRoundsBelowOne() {
        LandscapeTraversalDefinitions definitions =
                new LandscapeTraversalDefinitions();
        definitions.put(
                LandscapeDefinitionId.of(0),
                SurfaceTraversalCost.of(1));

        TransitionCostLowerBoundCalculator bounds =
                new TransitionCostLowerBoundCalculator(
                        definitions,
                        () -> 1);

        assertEquals(1, bounds.minimumEdgeCostUnits());
    }
}
