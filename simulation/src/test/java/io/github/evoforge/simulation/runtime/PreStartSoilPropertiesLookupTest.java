package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.soil.SoilProperties;
import org.junit.jupiter.api.Test;

final class PreStartSoilPropertiesLookupTest {
    @Test
    void usesFallbackUntilGeneratedLookupIsConfigured() {
        SoilProperties fallback = new SoilProperties(100, 10);
        SoilProperties generated = new SoilProperties(200, 20);
        PreStartSoilPropertiesLookup lookup = new PreStartSoilPropertiesLookup(
                (x, y, z) -> fallback);

        assertEquals(fallback, lookup.find(0, 0, 0));
        lookup.configure((x, y, z) -> generated);
        assertEquals(generated, lookup.find(0, 0, 0));
    }

    @Test
    void authoritativeGeneratedNullDoesNotFallBack() {
        SoilProperties fallback = new SoilProperties(100, 10);
        PreStartSoilPropertiesLookup lookup = new PreStartSoilPropertiesLookup(
                (x, y, z) -> fallback);

        lookup.configure((x, y, z) -> null);

        assertNull(lookup.find(0, 0, 0));
    }

    @Test
    void authoritativeLookupCanBeConfiguredOnlyOnceAndCannotChangeAfterFreeze() {
        PreStartSoilPropertiesLookup once = new PreStartSoilPropertiesLookup(
                (x, y, z) -> null);
        once.configure((x, y, z) -> null);
        assertThrows(
                IllegalStateException.class,
                () -> once.configure((x, y, z) -> null));

        PreStartSoilPropertiesLookup frozen = new PreStartSoilPropertiesLookup(
                (x, y, z) -> null);
        frozen.freeze();
        assertThrows(
                IllegalStateException.class,
                () -> frozen.configure((x, y, z) -> null));
    }
}
