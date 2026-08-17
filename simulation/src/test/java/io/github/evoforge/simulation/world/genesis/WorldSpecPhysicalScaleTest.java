package io.github.evoforge.simulation.world.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class WorldSpecPhysicalScaleTest {

    @Test
    void legacyConstructionKeepsPhysicalScaleUnspecified() {
        WorldSpec spec = new WorldSpec(new WorldBounds(0, 1, 0, 1, -1, 1));

        assertTrue(spec.physicalSpaceScale().isEmpty());
        assertThrows(IllegalStateException.class, spec::requirePhysicalSpaceScale);
    }

    @Test
    void explicitPhysicalScaleBecomesImmutableWorldProvenance() {
        WorldBounds bounds = new WorldBounds(-2, 2, -3, 3, -4, 4);
        PhysicalSpaceScale scale = new PhysicalSpaceScale(1_000L, 500L);
        WorldSpec spec = new WorldSpec(bounds, ClimateSpec.STANDARD, scale);

        assertEquals(Optional.of(scale), spec.physicalSpaceScale());
        assertEquals(scale, spec.requirePhysicalSpaceScale());
    }

    @Test
    void optionalContainerCannotBeNull() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -1, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldSpec(bounds, ClimateSpec.STANDARD, (Optional<PhysicalSpaceScale>) null));
    }
}
