package io.github.evoforge.simulation.world.bootstrap;

/**
 * @deprecated Closed compatibility selector retained for source migration only. Core runtime
 * composition depends on the open {@link AtmosphericRuntimePlan} seam instead.
 */
@Deprecated(forRemoval = true)
public enum AtmosphericForcingPolicy {
    CLIMATE_NORMALS(AtmosphericRuntimePlans.climateNormalsCompatibility()),
    WEATHER_STATE(AtmosphericRuntimePlans.calmWeatherState()),
    DISABLED(AtmosphericRuntimePlans.disabled());

    private final AtmosphericRuntimePlan runtimePlan;

    AtmosphericForcingPolicy(AtmosphericRuntimePlan runtimePlan) {
        this.runtimePlan = runtimePlan;
    }

    AtmosphericRuntimePlan runtimePlan() {
        return runtimePlan;
    }
}
