package io.github.evoforge.simulation.world.landscape.liquid;

/**
 * Transport properties of one liquid constituent.
 *
 * <p>Kinematic viscosity is stored in square micrometres per second. The discrete
 * solver uses viscosity only through deterministic mobility ratios; it never
 * branches on concrete liquid identities.
 */
public record LiquidTransportProperties(
        long kinematicViscositySquareMicrometersPerSecond) {

    /** Nominal 1 mm^2/s reference used to calibrate one simulation transport step. */
    public static final long REFERENCE_KINEMATIC_VISCOSITY = 1_000_000L;

    public LiquidTransportProperties {
        if (kinematicViscositySquareMicrometersPerSecond <= 0L) {
            throw new IllegalArgumentException(
                    "kinematic viscosity must be positive: "
                            + kinematicViscositySquareMicrometersPerSecond);
        }
    }

    public static LiquidTransportProperties ofKinematicViscosity(
            long squareMicrometersPerSecond) {
        return new LiquidTransportProperties(squareMicrometersPerSecond);
    }

    public static LiquidTransportProperties reference() {
        return new LiquidTransportProperties(REFERENCE_KINEMATIC_VISCOSITY);
    }
}
