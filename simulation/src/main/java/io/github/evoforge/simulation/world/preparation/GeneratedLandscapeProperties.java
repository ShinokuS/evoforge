package io.github.evoforge.simulation.world.preparation;

import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Optional;

/** Immutable generated physical landscape properties carried from preparation toward runtime. */
public final class GeneratedLandscapeProperties {
    private final WorldBounds bounds;
    private final Optional<SoilHydraulicProfileField> soilHydraulics;

    public GeneratedLandscapeProperties(SoilHydraulicProfileField soilHydraulics) {
        if (soilHydraulics == null) {
            throw new IllegalArgumentException("generated Soil hydraulics must not be null");
        }
        this.bounds = soilHydraulics.bounds();
        this.soilHydraulics = Optional.of(soilHydraulics);
    }

    private GeneratedLandscapeProperties(WorldBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("generated landscape bounds must not be null");
        }
        this.bounds = bounds;
        this.soilHydraulics = Optional.empty();
    }

    public WorldBounds bounds() {
        return bounds;
    }

    /**
     * Returns the authoritative generated Soil field when preparation produced one.
     *
     * <p>Absence means runtime may use its ordinary definition-level fallback. A present field may
     * itself return {@code null} for a particular coordinate, which authoritatively means that
     * generated Terrain cell is non-porous and must not fall back to a material-wide Soil value.</p>
     */
    public Optional<SoilHydraulicProfileField> soilHydraulics() {
        return soilHydraulics;
    }

    public static GeneratedLandscapeProperties empty(WorldBounds bounds) {
        return new GeneratedLandscapeProperties(bounds);
    }
}
