package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable generated physical Soil hydraulics over XYZ world space. */
public interface SoilHydraulicProfileField {
    WorldBounds bounds();

    /** Returns the generated physical profile, or {@code null} when the cell has no Soil profile. */
    SoilHydraulicProfile find(int x, int y, int z);
}
