package io.github.evoforge.simulation.world.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainLookup;
import org.junit.jupiter.api.Test;
import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.geometry.TransitionPorts;

final class GeometryTraversalLowerBoundTest {

    @Test
    void defaultsToNeutralAndTracksCurrentOverrides() {
        Map<String, LandscapeDefinitionId> terrain = new HashMap<>();
        LandscapeDefinitionId ground = LandscapeDefinitionId.of(0);
        terrain.put("0,0,0", ground);
        terrain.put("1,0,0", ground);

        TerrainLookup lookup =
                (x, y, z) -> terrain.get(x + "," + y + "," + z);
        GeometrySystem geometry = new GeometrySystem(lookup);

        assertEquals(
                ShapeTraversalFactor.NEUTRAL,
                geometry.traversalBounds().minimumTraversalFactor());

        Shape slowFloor = new LowerBoundShape(400);
        geometry.setShape(0, 0, 0, slowFloor);
        assertEquals(
                400,
                geometry.traversalBounds().minimumTraversalFactor());

        geometry.setShape(1, 0, 0, new LowerBoundShape(700));
        assertEquals(
                400,
                geometry.traversalBounds().minimumTraversalFactor());

        geometry.clearShapeOverride(0, 0, 0);
        assertEquals(
                700,
                geometry.traversalBounds().minimumTraversalFactor());

        geometry.setShape(1, 0, 0, FullShape.INSTANCE);
        assertEquals(
                ShapeTraversalFactor.NEUTRAL,
                geometry.traversalBounds().minimumTraversalFactor());
    }

    private static final class LowerBoundShape implements Shape {

        private final int minimum;

        private LowerBoundShape(int minimum) {
            this.minimum = minimum;
        }

        @Override
        public int minimumTraversalFactor() {
            return minimum;
        }

        @Override
        public long transitionPorts(
                int relativeX,
                int relativeY,
                int relativeZ) {
            return TransitionPorts.NONE;
        }
    }
}
