package io.github.evoforge.simulation.world.atlas;

/** Converts an authored inland-lake domain into a Z=0-compatible pre-mountain terrain boundary. */
@FunctionalInterface
public interface InlandLakeShoreConditioningAlgorithm {
    ElevationField condition(
            ElevationField continentalBase,
            InlandLakeDomain lakeDomain);

    static InlandLakeShoreConditioningAlgorithm standard() {
        return Z0InlandLakeShoreConditioningAlgorithm.INSTANCE;
    }
}
