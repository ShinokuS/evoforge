package io.github.evoforge.simulation.world.geophysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.definition.NormalizedValue;
import org.junit.jupiter.api.Test;

final class MacroGeophysicsDefinitionTest {

    @Test
    void semanticControlsRemainNormalizedAuthoredMeaning() {
        MacroGeophysicsDefinition definition = MacroGeophysicsDefinition.of(0.70d, 0.60d, 0.50d, 0.40d, 0.30d);

        assertEquals(NormalizedValue.of(0.70d), definition.oceanPrevalence());
        assertEquals(NormalizedValue.of(0.60d), definition.continentalScale());
        assertEquals(NormalizedValue.of(0.50d), definition.landmassCohesion());
        assertEquals(NormalizedValue.of(0.40d), definition.fragmentation());
        assertEquals(NormalizedValue.of(0.30d), definition.macroVariation());
    }

    @Test
    void invalidOrMissingControlsAreRejectedAtTheSemanticBoundary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MacroGeophysicsDefinition.of(-0.01d, 0.5d, 0.5d, 0.5d, 0.5d));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MacroGeophysicsDefinition(
                        null,
                        NormalizedValue.of(0.5d),
                        NormalizedValue.of(0.5d),
                        NormalizedValue.of(0.5d),
                        NormalizedValue.of(0.5d)));
    }

    @Test
    void presetsAreConveniencesOverDistinctDefinitions() {
        assertNotEquals(
                MacroGeophysicsPreset.SUPERCONTINENT.definition(),
                MacroGeophysicsPreset.ARCHIPELAGO.definition());
        assertNotEquals(
                MacroGeophysicsPreset.BALANCED.definition(),
                MacroGeophysicsPreset.OCEANIC.definition());
        assertEquals("supercontinent", MacroGeophysicsPreset.SUPERCONTINENT.displayName());
    }

    @Test
    void publicFactoryRejectsMissingDefinition() {
        assertThrows(IllegalArgumentException.class, () -> MacroGeophysics.create(1L, 1L, null));
    }
}
