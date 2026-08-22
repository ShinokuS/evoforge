package io.github.evoforge.simulation.world.geometry;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.space.WorldBounds;

final class WorldGeometryLookupTest {

    @Test
    void unconfiguredLookupPreservesUnboundedDelegateSemantics() {
        WorldGeometryLookup geometry = new WorldGeometryLookup(
                (x, y, z) -> null);

        assertNull(geometry.find(Integer.MAX_VALUE, Integer.MIN_VALUE, 0));
        assertTrue(geometry.contains(Integer.MAX_VALUE, Integer.MIN_VALUE, 0));
    }

    @Test
    void configuredBoundsExposeOutsideSpaceAsPhysicallyClosed() {
        WorldGeometryLookup geometry = new WorldGeometryLookup(
                (x, y, z) -> null);
        geometry.configureBounds(new WorldBounds(-2, 2, -3, 3, -1, 4));

        assertNull(geometry.find(2, 3, 4));
        assertSame(FullShape.INSTANCE, geometry.find(3, 0, 0));
        assertSame(FullShape.INSTANCE, geometry.find(0, 4, 0));
        assertSame(FullShape.INSTANCE, geometry.find(0, 0, 5));
    }

    @Test
    void boundsCanOnlyBeConfiguredOnce() {
        WorldGeometryLookup geometry = new WorldGeometryLookup(
                (x, y, z) -> null);
        geometry.configureBounds(new WorldBounds(0, 1, 0, 1, 0, 1));

        assertThrows(
                IllegalStateException.class,
                () -> geometry.configureBounds(
                        new WorldBounds(0, 2, 0, 2, 0, 2)));
    }
}
