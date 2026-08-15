package io.github.evoforge.simulation.world.landscape.liquid;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Deterministic fixed-point transport math shared by flow and porous infiltration. */
public final class LiquidTransportMath {

    private static final int FLOW_RELAXATION_DENOMINATOR = 2;

    private LiquidTransportMath() {
    }

    /**
     * Converts a nominal reference-fluid rate into this liquid's rate using
     * inverse kinematic viscosity. The result is bounded to one cell volume.
     */
    public static int mobilityAdjustedAmount(
            int nominalAmount,
            LiquidTransportProperties properties) {

        CellVolume.requireValid(nominalAmount);
        requireProperties(properties);
        if (nominalAmount == CellVolume.EMPTY) return CellVolume.EMPTY;

        long numerator = Math.multiplyExact(
                (long) nominalAmount,
                LiquidTransportProperties.REFERENCE_KINEMATIC_VISCOSITY);
        long adjusted = numerator
                / properties.kinematicViscositySquareMicrometersPerSecond();
        return (int) Math.min((long) CellVolume.FULL, adjusted);
    }

    /**
     * Advances one numerically relaxed hydraulic step. Viscosity changes how much
     * of the equilibrium transfer is realized this tick; the transfer never
     * overshoots the equilibrium amount.
     */
    public static int relaxedFlowAmount(
            int equilibriumAmount,
            LiquidTransportProperties properties) {

        CellVolume.requireValid(equilibriumAmount);
        requireProperties(properties);
        if (equilibriumAmount == CellVolume.EMPTY) return CellVolume.EMPTY;

        int nominalRelaxed = equilibriumAmount / FLOW_RELAXATION_DENOMINATOR;
        int adjusted = mobilityAdjustedAmount(nominalRelaxed, properties);
        return Math.min(equilibriumAmount, adjusted);
    }

    private static void requireProperties(LiquidTransportProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException(
                    "liquid transport properties must not be null");
        }
    }
}
