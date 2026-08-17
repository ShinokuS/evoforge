package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesLookup;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.HashMap;
import java.util.Map;

/**
 * Pre-start bridge from immutable generated hydraulic facts to the runtime Soil lookup contract.
 *
 * <p>The generated field remains authoritative per solid coordinate. Physical profiles are
 * compiled once per distinct value during preflight; runtime lookup never recalibrates or converts
 * physical units. A {@code null} profile remains an authoritative non-porous cell rather than
 * falling back to material-wide Definition data.</p>
 */
public final class SoilHydraulicRuntimeFieldCompiler {
    private SoilHydraulicRuntimeFieldCompiler() { }

    public static SoilPropertiesLookup compile(
            SoilHydraulicProfileField field,
            ElevationField elevation,
            PhysicalSpaceScale spaceScale,
            SimulationTimeScale timeScale) {
        if (field == null || elevation == null || spaceScale == null || timeScale == null) {
            throw new IllegalArgumentException(
                    "generated Soil runtime compilation inputs must not be null");
        }
        WorldBounds bounds = elevation.bounds();
        if (!bounds.equals(field.bounds())) {
            throw new IllegalArgumentException(
                    "generated Soil hydraulic bounds must match generated elevation bounds");
        }

        Map<SoilHydraulicProfile, SoilProperties> compiled = new HashMap<>();
        for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
            int worldX = (int) x;
            for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
                int worldY = (int) y;
                int surfaceZ = elevation.elevationAt(worldX, worldY);
                if (surfaceZ < bounds.minZ() || surfaceZ > bounds.maxZ()) {
                    throw new IllegalStateException(
                            "generated elevation surface is outside world bounds at ("
                                    + worldX + ", " + worldY + ", " + surfaceZ + ")");
                }
                for (long z = bounds.minZ(); z <= (long) surfaceZ; z++) {
                    SoilHydraulicProfile profile = field.find(worldX, worldY, (int) z);
                    if (profile != null) {
                        compiled.computeIfAbsent(
                                profile,
                                value -> SoilHydraulicRuntimeCompiler.compile(
                                        value, spaceScale, timeScale));
                    }
                }
            }
        }

        Map<SoilHydraulicProfile, SoilProperties> immutable = Map.copyOf(compiled);
        return (x, y, z) -> {
            if (!bounds.contains(x, y, z)) {
                return null;
            }
            int surfaceZ = elevation.elevationAt(x, y);
            if (z > surfaceZ) {
                return null;
            }
            SoilHydraulicProfile profile = field.find(x, y, z);
            if (profile == null) {
                return null;
            }
            SoilProperties properties = immutable.get(profile);
            if (properties == null) {
                throw new IllegalStateException(
                        "generated Soil hydraulic field changed after pre-start compilation at ("
                                + x + ", " + y + ", " + z + ")");
            }
            return properties;
        };
    }
}
