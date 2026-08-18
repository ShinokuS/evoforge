package io.github.evoforge.simulation.world.terrain.generation;

import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.geology.GeologyField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.surface.SurfaceMorphologyField;
import io.github.evoforge.simulation.world.terrain.surface.SurfaceMorphologyGenerationStage;

/**
 * First causal material-strata model.
 *
 * <p>Compiled profiles select reusable process capabilities and semantic surface material roles.
 * Generated Geology owns the deeper rock identity when present. Numeric slope/deposition policy is
 * generated complexity owned by this versioned algorithm, never a required content-author knob.</p>
 */
public final class TerrainMaterialGenerationStage implements TerrainMaterialGenerator {
    private static final int MAX_GROUND_DEPTH_CELLS = 4;
    private static final int MAX_DEPOSITION_DEPTH_CELLS = 2;

    /* Model-v1 normalized constants, expressed in precise elevation cell subunits. */
    private static final long SOIL_SLOPE_STEP = 250_000L;
    private static final long DEPOSITION_MAX_SLOPE = 550_000L;
    private static final long DEPOSITION_THRESHOLD = 120_000L;
    private static final long DEEP_DEPOSITION_THRESHOLD = 550_000L;

    @Override
    public TerrainMaterialField generate(
            ElevationField elevation,
            DrainageField drainage,
            CompiledTerrainProfile profile) {
        return generateInternal(
                elevation,
                null,
                drainage,
                null,
                new SurfaceMorphologyGenerationStage().generate(elevation),
                profile);
    }

    @Override
    public TerrainMaterialField generate(
            ElevationField elevation,
            DrainageField drainage,
            SurfaceHydrologyField surfaceHydrology,
            CompiledTerrainProfile profile) {
        if (surfaceHydrology == null) {
            throw new IllegalArgumentException("surfaceHydrology must not be null");
        }
        return generateInternal(
                elevation,
                null,
                drainage,
                surfaceHydrology,
                new SurfaceMorphologyGenerationStage().generate(elevation),
                profile);
    }

    @Override
    public TerrainMaterialField generate(
            ElevationField elevation,
            GeologyField geology,
            DrainageField drainage,
            SurfaceHydrologyField surfaceHydrology,
            CompiledTerrainProfile profile) {
        if (geology == null || surfaceHydrology == null) {
            throw new IllegalArgumentException("geology and surfaceHydrology must not be null");
        }
        return generateInternal(
                elevation,
                geology,
                drainage,
                surfaceHydrology,
                new SurfaceMorphologyGenerationStage().generate(elevation),
                profile);
    }

    @Override
    public TerrainMaterialField generate(
            ElevationField elevation,
            GeologyField geology,
            DrainageField drainage,
            SurfaceHydrologyField surfaceHydrology,
            SurfaceMorphologyField morphology,
            CompiledTerrainProfile profile) {
        if (geology == null || surfaceHydrology == null || morphology == null) {
            throw new IllegalArgumentException(
                    "geology, surface hydrology and morphology must not be null");
        }
        return generateInternal(
                elevation,
                geology,
                drainage,
                surfaceHydrology,
                morphology,
                profile);
    }

    private TerrainMaterialField generateInternal(
            ElevationField elevation,
            GeologyField geology,
            DrainageField drainage,
            SurfaceHydrologyField surfaceHydrology,
            SurfaceMorphologyField morphology,
            CompiledTerrainProfile profile) {
        if (elevation == null || drainage == null || morphology == null || profile == null) {
            throw new IllegalArgumentException(
                    "terrain material generation dependencies must not be null");
        }
        WorldBounds bounds = elevation.bounds();
        if (!bounds.equals(drainage.bounds())) {
            throw new IllegalArgumentException("elevation and drainage bounds must match");
        }
        if (!bounds.equals(morphology.bounds())) {
            throw new IllegalArgumentException("elevation and surface morphology bounds must match");
        }
        if (geology != null && !bounds.equals(geology.bounds())) {
            throw new IllegalArgumentException("geology bounds must match terrain generation bounds");
        }
        if (surfaceHydrology != null && !bounds.equals(surfaceHydrology.bounds())) {
            throw new IllegalArgumentException(
                    "surface hydrology bounds must match terrain generation bounds");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        long height = (long) bounds.maxY() - bounds.minY() + 1L;
        long areaLong = Math.multiplyExact((long) width, height);
        if (areaLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "horizontal world area exceeds current terrain-profile representation: "
                            + areaLong);
        }
        int area = (int) areaLong;
        int[] surfaceZ = new int[area];
        byte[] groundDepth = new byte[area];
        byte[] depositionDepth = new byte[area];

        boolean groundProfile = profile.has(TerrainPresetCapability.GROUND_PROFILE);
        boolean surfaceDeposition = profile.has(TerrainPresetCapability.SURFACE_DEPOSITION);

        int index = 0;
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                int surface = elevation.elevationAt(worldX, worldY);
                if (surface < bounds.minZ() || surface > bounds.maxZ()) {
                    throw new IllegalStateException(
                            "generated surface outside world bounds at ("
                                    + worldX + ", " + worldY + "): " + surface);
                }
                surfaceZ[index] = surface;

                LocalSurfaceContext context = contextAt(
                        morphology,
                        drainage,
                        areaLong,
                        worldX,
                        worldY);

                groundDepth[index] = (byte) (groundProfile
                        ? groundDepthFor(context.maximumSlope())
                        : 0);
                int deposition = surfaceDeposition ? depositionDepthFor(context) : 0;
                if (surfaceDeposition
                        && surfaceHydrology != null
                        && surfaceHydrology.isShoreline(worldX, worldY)) {
                    deposition = Math.max(1, deposition);
                }
                depositionDepth[index] = (byte) deposition;
                index++;
            }
        }

        return new LayeredTerrainMaterialField(
                bounds,
                width,
                surfaceZ,
                groundDepth,
                depositionDepth,
                geology,
                profile.materials());
    }

    private static int groundDepthFor(long slope) {
        long steps = slope == 0L ? 0L : 1L + (slope - 1L) / SOIL_SLOPE_STEP;
        long depth = (long) MAX_GROUND_DEPTH_CELLS - steps;
        if (depth <= 0L) return 0;
        return (int) Math.min(depth, MAX_GROUND_DEPTH_CELLS);
    }

    private static int depositionDepthFor(LocalSurfaceContext context) {
        if (context.maximumSlope() > DEPOSITION_MAX_SLOPE) return 0;

        long score = Math.addExact(
                Math.multiplyExact(context.concavity(), 2L),
                context.drainageInfluence());
        score = Math.subtractExact(score, context.maximumSlope());
        if (score < DEPOSITION_THRESHOLD) return 0;
        return score >= DEEP_DEPOSITION_THRESHOLD ? MAX_DEPOSITION_DEPTH_CELLS : 1;
    }

    private static LocalSurfaceContext contextAt(
            SurfaceMorphologyField morphology,
            DrainageField drainage,
            long horizontalArea,
            int x,
            int y) {
        long drainageInfluence = Math.multiplyExact(
                drainage.contributingAreaAt(x, y),
                ElevationField.SUBUNITS_PER_CELL) / horizontalArea;
        return new LocalSurfaceContext(
                morphology.maximumNeighborSlopeSubunitsAt(x, y),
                morphology.concavitySubunitsAt(x, y),
                drainageInfluence);
    }

    private record LocalSurfaceContext(
            long maximumSlope,
            long concavity,
            long drainageInfluence) { }

    private static final class LayeredTerrainMaterialField implements TerrainMaterialField {
        private final WorldBounds bounds;
        private final int width;
        private final int[] surfaceZ;
        private final byte[] groundDepth;
        private final byte[] depositionDepth;
        private final GeologyField geology;
        private final TerrainMaterialSet materials;

        private LayeredTerrainMaterialField(
                WorldBounds bounds,
                int width,
                int[] surfaceZ,
                byte[] groundDepth,
                byte[] depositionDepth,
                GeologyField geology,
                TerrainMaterialSet materials) {
            this.bounds = bounds;
            this.width = width;
            this.surfaceZ = surfaceZ;
            this.groundDepth = groundDepth;
            this.depositionDepth = depositionDepth;
            this.geology = geology;
            this.materials = materials;
        }

        @Override
        public WorldBounds bounds() {
            return bounds;
        }

        @Override
        public TerrainMaterialKey materialAt(int x, int y, int z) {
            if (x < bounds.minX() || x > bounds.maxX()
                    || y < bounds.minY() || y > bounds.maxY()) {
                throw new IllegalArgumentException(
                        "terrain material column outside world bounds: (" + x + ", " + y + ")");
            }
            int index = (y - bounds.minY()) * width + (x - bounds.minX());
            int surface = surfaceZ[index];
            if (z < bounds.minZ() || z > surface) {
                throw new IllegalArgumentException(
                        "terrain material lookup outside generated solid column: ("
                                + x + ", " + y + ", " + z + ")");
            }

            int depth = surface - z;
            int sediment = Byte.toUnsignedInt(depositionDepth[index]);
            if (depth < sediment) {
                return materials.require(TerrainMaterialRole.SEDIMENT);
            }

            int ground = Byte.toUnsignedInt(groundDepth[index]);
            if (ground > 0) {
                if (depth == 0) {
                    return materials.require(TerrainMaterialRole.SURFACE);
                }
                if (depth < ground) {
                    return materials.require(TerrainMaterialRole.SUBSURFACE);
                }
            }
            if (geology != null) {
                return TerrainMaterialKey.of(geology.materialAt(x, y, z).value());
            }
            return materials.require(TerrainMaterialRole.BEDROCK);
        }
    }
}
