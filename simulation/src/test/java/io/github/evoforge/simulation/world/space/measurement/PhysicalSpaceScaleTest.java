package io.github.evoforge.simulation.world.space.measurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

final class PhysicalSpaceScaleTest {

    @Test
    void derivesExactAreaVolumeAndLegacyMilliliterBridge() {
        PhysicalSpaceScale scale = PhysicalSpaceScale.cubicMillimeters(1_000L);

        assertEquals(
                BigInteger.valueOf(1_000_000L),
                scale.horizontalCellAreaSquareMillimeters());
        assertEquals(
                BigInteger.valueOf(1_000_000_000L),
                scale.fullCellVolumeCubicMillimeters());
        assertEquals(
                1_000_000L,
                scale.physicalCellVolumeExact().millilitersPerFullCell());
    }

    @Test
    void derivedGeometryDoesNotOverflowLongArithmetic() {
        PhysicalSpaceScale scale = new PhysicalSpaceScale(Long.MAX_VALUE, Long.MAX_VALUE);

        assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE).pow(2),
                scale.horizontalCellAreaSquareMillimeters());
        assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE).pow(3),
                scale.fullCellVolumeCubicMillimeters());
    }

    @Test
    void rejectsInvalidDimensionsAndFractionalLegacyMilliliterConversion() {
        assertThrows(IllegalArgumentException.class, () -> new PhysicalSpaceScale(0L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalSpaceScale(1L, 0L));
        assertThrows(
                IllegalStateException.class,
                () -> PhysicalSpaceScale.cubicMillimeters(1L).physicalCellVolumeExact());
    }
}
