package io.github.evoforge.simulation.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class NormalizedValueTest {

    @Test
    void parsesAuthoredDecimalIntoExactFixedPointCoordinate() {
        assertEquals(400_000, NormalizedValue.parse("0.4").partsPerMillion());
        assertEquals(734_125, NormalizedValue.parse("0.734125").partsPerMillion());
        assertEquals(0, NormalizedValue.parse("0").partsPerMillion());
        assertEquals(NormalizedValue.SCALE, NormalizedValue.parse("1").partsPerMillion());
    }

    @Test
    void rejectsOutOfRangeOrOverPreciseAuthoredCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> NormalizedValue.parse("-0.000001"));
        assertThrows(IllegalArgumentException.class, () -> NormalizedValue.parse("1.000001"));
        assertThrows(IllegalArgumentException.class, () -> NormalizedValue.parse("0.1234567"));
    }
}
