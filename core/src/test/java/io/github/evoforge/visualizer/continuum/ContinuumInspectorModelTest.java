package io.github.evoforge.visualizer.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import org.junit.jupiter.api.Test;

final class ContinuumInspectorModelTest {

    @Test
    void initialViewRequestsOnlyThreeByThreeNeighborhood() {
        ContinuumInspectorModel model = model();

        assertEquals(9, model.requestedKeys().size());
        assertEquals(9, model.residentKeys().size());
        assertEquals(9L, model.metrics().loads());
        assertEquals(9L, model.metrics().misses());
        assertEquals(0L, model.metrics().evictions());
    }

    @Test
    void movingFocusReusesOverlapThenEvictsOnlyTechnicalResidency() {
        ContinuumInspectorModel model = model();
        ContinuumPageKey originalFocus = model.focus();
        double originalValue = model.requestedValue(originalFocus).orElseThrow();

        model.moveFocus(1L, 0L);
        assertEquals(6L, model.metrics().hits());
        assertEquals(12L, model.metrics().loads());
        assertEquals(12, model.metrics().residentPages());
        assertTrue(model.lastEvictedKeys().isEmpty());

        model.moveFocus(1L, 0L);
        assertEquals(12L, model.metrics().hits());
        assertEquals(15L, model.metrics().loads());
        assertEquals(12, model.metrics().residentPages());
        assertEquals(3L, model.metrics().evictions());
        assertFalse(model.lastEvictedKeys().isEmpty());

        model.jumpToPage(originalFocus.pageX(), originalFocus.pageY());
        assertEquals(originalValue, model.requestedValue(originalFocus).orElseThrow());
    }

    @Test
    void hugePageJumpsClampInsideLogicalDomainWithoutAllocatingWorldArea() {
        ContinuumInspectorModel model = model();

        model.jumpToPage(Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(model.pageCountX() - 1L, model.focus().pageX());
        assertEquals(model.pageCountY() - 1L, model.focus().pageY());
        assertTrue(model.requestedKeys().size() <= 9);
        assertTrue(model.metrics().residentPages() <= model.metrics().maxResidentPages());
        assertTrue(model.metrics().residentPayloadBytes() <= model.metrics().maxResidentPayloadBytes());

        model.moveFocus(Long.MIN_VALUE, Long.MIN_VALUE);
        assertEquals(0L, model.focus().pageX());
        assertEquals(0L, model.focus().pageY());
    }

    private static ContinuumInspectorModel model() {
        return new ContinuumInspectorModel(
                1_000_000L,
                256,
                12,
                12345L,
                (x, y) -> x * 0.25d + y * 0.5d);
    }
}
