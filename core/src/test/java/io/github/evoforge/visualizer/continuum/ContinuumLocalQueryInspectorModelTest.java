package io.github.evoforge.visualizer.continuum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ContinuumLocalQueryInspectorModelTest {
    @Test
    void oneTenAndOneHundredConsumersKeepTheSameFourSharedRegions() {
        ContinuumLocalQueryInspectorModel model = new ContinuumLocalQueryInspectorModel();

        model.setConsumerCount(1);
        assertEquals(4, model.batch().metrics().uniqueRegions());
        assertEquals(0, model.batch().metrics().reusedRegionUses());

        model.setConsumerCount(10);
        assertEquals(4, model.batch().metrics().uniqueRegions());
        assertEquals(36, model.batch().metrics().reusedRegionUses());

        model.setConsumerCount(100);
        assertEquals(4, model.batch().metrics().uniqueRegions());
        assertEquals(396, model.batch().metrics().reusedRegionUses());
        assertEquals(4L, model.batch().metrics().pageLoads());
    }

    @Test
    void movingTheDemoChangesLocationWithoutChangingTheSharingRule() {
        ContinuumLocalQueryInspectorModel model = new ContinuumLocalQueryInspectorModel();
        long beforeX = model.boundaryX();

        model.moveByPages(1L, 0L);

        assertEquals(beforeX + ContinuumLocalQueryInspectorModel.PAGE_SIDE, model.boundaryX());
        assertEquals(4, model.batch().metrics().uniqueRegions());
        assertEquals(36, model.batch().metrics().reusedRegionUses());
    }

    @Test
    void revisionChangeIsVisibleButKeepsTheSameLocalProof() {
        ContinuumLocalQueryInspectorModel model = new ContinuumLocalQueryInspectorModel();

        model.advanceRevision();

        assertEquals(1L, model.revision());
        assertEquals(1L, model.batch().revision());
        assertTrue(model.batch().views().stream().allMatch(view -> view.revision() == 1L));
    }
}
