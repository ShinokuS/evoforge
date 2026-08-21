package io.github.evoforge.simulation.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class NormalizedValueTest {

    @Test
    void exposesHumanAuthoredZeroToOneCoordinatesDirectly() {
        assertEquals(0.0, NormalizedValue.parse("0").value());
        assertEquals(0.4, NormalizedValue.parse("0.4").value());
        assertEquals(0.734125, NormalizedValue.of(0.734125).value());
        assertEquals(1.0, NormalizedValue.parse("1").value());
    }

    @Test
    void rejectsInvalidUnsignedCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> NormalizedValue.of(-0.000001));
        assertThrows(IllegalArgumentException.class, () -> NormalizedValue.of(1.000001));
        assertThrows(IllegalArgumentException.class, () -> NormalizedValue.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> NormalizedValue.parse("not-a-number"));
    }

    @Test
    void exposesHumanAuthoredSignedCoordinatesDirectly() {
        assertEquals(-1.0, SignedNormalizedValue.parse("-1").value());
        assertEquals(-0.25, SignedNormalizedValue.of(-0.25).value());
        assertEquals(0.0, SignedNormalizedValue.parse("0").value());
        assertEquals(0.75, SignedNormalizedValue.of(0.75).value());
        assertEquals(1.0, SignedNormalizedValue.parse("1").value());
    }

    @Test
    void rejectsInvalidSignedCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> SignedNormalizedValue.of(-1.000001));
        assertThrows(IllegalArgumentException.class, () -> SignedNormalizedValue.of(1.000001));
        assertThrows(IllegalArgumentException.class, () -> SignedNormalizedValue.of(Double.POSITIVE_INFINITY));
    }
}
