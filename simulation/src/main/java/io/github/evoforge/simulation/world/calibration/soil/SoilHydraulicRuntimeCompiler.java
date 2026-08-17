package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRateCellVolumeCompiler;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import java.math.BigInteger;

/**
 * Deterministic boundary from physical soil hydraulics into the current runtime Soil contract.
 *
 * <p>Porosity becomes pore capacity relative to one full cell. Saturated hydraulic conductivity
 * becomes the maximum normalized infiltration volume that can cross the horizontal surface during
 * one physical simulation tick.</p>
 *
 * <p>The current runtime stores permeability as an integer CellVolume amount per tick. This bridge
 * therefore rejects physical combinations that require fractional normalized units instead of
 * rounding them. A future rational infiltration runtime can remove that representational
 * restriction without changing {@link SoilHydraulicProfile}.</p>
 */
public final class SoilHydraulicRuntimeCompiler {

    private static final BigInteger CELL_VOLUME_FULL = BigInteger.valueOf(CellVolume.FULL);
    private static final BigInteger FRACTION_SCALE =
            BigInteger.valueOf(SoilHydraulicProfile.FRACTION_SCALE);

    private SoilHydraulicRuntimeCompiler() {
    }

    public static SoilProperties compile(
            SoilHydraulicProfile profile,
            PhysicalSpaceScale spaceScale,
            SimulationTimeScale timeScale) {
        if (profile == null || spaceScale == null || timeScale == null) {
            throw new IllegalArgumentException(
                    "soil hydraulic compilation inputs must not be null");
        }

        int capacity = compileCapacity(profile);
        CellVolumeRate conductivity = WaterDepthRateCellVolumeCompiler.compile(
                profile.saturatedHydraulicConductivity(),
                spaceScale,
                timeScale);
        if (conductivity.tickDenominator() != 1L) {
            throw new IllegalArgumentException(
                    "current Soil runtime cannot represent fractional infiltration per tick: "
                            + conductivity.volumeUnitsNumerator() + "/"
                            + conductivity.tickDenominator()
                            + " CellVolume/tick");
        }
        if (conductivity.volumeUnitsNumerator() > CellVolume.FULL) {
            throw new IllegalArgumentException(
                    "current Soil runtime cannot represent infiltration above one full cell per tick: "
                            + conductivity.volumeUnitsNumerator());
        }

        return new SoilProperties(
                capacity,
                Math.toIntExact(conductivity.volumeUnitsNumerator()));
    }

    private static int compileCapacity(SoilHydraulicProfile profile) {
        BigInteger numerator = CELL_VOLUME_FULL.multiply(
                BigInteger.valueOf(profile.porosityPartsPerMillion()));
        BigInteger[] division = numerator.divideAndRemainder(FRACTION_SCALE);
        if (division[1].signum() != 0) {
            throw new IllegalArgumentException(
                    "soil porosity cannot be represented by current CellVolume resolution");
        }
        return division[0].intValueExact();
    }
}
