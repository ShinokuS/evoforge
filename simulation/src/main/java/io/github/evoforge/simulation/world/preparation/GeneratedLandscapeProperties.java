package io.github.evoforge.simulation.world.preparation;

import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable generated physical landscape properties carried from preparation toward runtime. */
public record GeneratedLandscapeProperties(SoilHydraulicProfileField soilHydraulics) {
    public GeneratedLandscapeProperties {
        if (soilHydraulics == null) {
            throw new IllegalArgumentException("generated Soil hydraulics must not be null");
        }
    }

    public WorldBounds bounds() {
        return soilHydraulics.bounds();
    }

    public static GeneratedLandscapeProperties empty(WorldBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("generated landscape bounds must not be null");
        }
        return new GeneratedLandscapeProperties(new EmptySoilHydraulics(bounds));
    }

    private record EmptySoilHydraulics(WorldBounds bounds) implements SoilHydraulicProfileField {
        private EmptySoilHydraulics {
            if (bounds == null) throw new IllegalArgumentException("bounds must not be null");
        }

        @Override
        public SoilHydraulicProfile find(int x, int y, int z) {
            return null;
        }
    }
}
