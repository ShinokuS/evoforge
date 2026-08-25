package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalField;

/**
 * Bounded deterministic Stage 6 surface model hidden behind {@link TerrainSurfaceEvolution}.
 *
 * <p>Stage 5 remains the world-scale land/ocean support. Stage 6 turns that support into a
 * continuous physical surface using broad provinces, sparse finite mountain systems, plateau and
 * lowland interiors, and progressively smaller subordinate relief. Mountain systems are finite
 * curved regional features rather than zero-crossing contour bands, preventing the worm-like
 * global ridge networks that an earlier prototype produced.</p>
 *
 * <p>All local relief is strongly suppressed through the coastal transition and the final surface
 * preserves the Stage 5 side of the shared sea datum. Stage 6 therefore enriches coastal relief
 * without punching accidental lakes, lagoons, or islands into the macro landmass silhouette.
 * Drainage-connected water topology remains a later Genesis responsibility.</p>
 */
final class DeterministicContinuousTerrainSurface implements ContinuousTerrainSurface {
    private static final long MIN_REGIONAL_SPAN = 1L << 18;
    private static final long MAX_REGIONAL_SPAN = 1L << 20;
    private static final long MIN_MEDIUM_SPAN = 1L << 14;
    private static final long MIN_FINE_SPAN = 1L << 12;
    private static final long MIN_MICRO_SPAN = 1L << 10;
    private static final double INV_SQRT_2 = 0.7071067811865476d;
    private static final double MIN_SURFACE_Z = -4_096.0d;
    private static final double MAX_SURFACE_Z = 4_096.0d;

    private static final long WARP_X_SALT = 0x4C9A731DB8E2056FL;
    private static final long WARP_Y_SALT = 0x91E64B2AC7D83510L;
    private static final long PROVINCE_SALT = 0xC8047E31B5A269DFL;
    private static final long TECTONIC_SALT = 0x5F28C1B96A73D40EL;
    private static final long MEDIUM_RELIEF_SALT = 0x739E25B4C1D86A0FL;
    private static final long FINE_RELIEF_SALT = 0x0D54A7C9E31B682FL;
    private static final long MICRO_RELIEF_SALT = 0xB6F10C4D29A875E3L;
    private static final long OCEAN_BROAD_SALT = 0x3A71D6C5E92B408FL;
    private static final long OCEAN_MEDIUM_SALT = 0x8D25F0B143CE769AL;
    private static final long MOUNTAIN_RANGE_SALT = 0xD6A8F241C30B597EL;

    private static final long ACTIVE_VARIANT = 0x9E3779B97F4A7C15L;
    private static final long CENTER_X_VARIANT = 0xA24BAED4963EE407L;
    private static final long CENTER_Y_VARIANT = 0x9FB21C651E98DF25L;
    private static final long ANGLE_VARIANT = 0xC13FA9A902A6328FL;
    private static final long LENGTH_VARIANT = 0x91E10DA5C79E7B1DL;
    private static final long WIDTH_VARIANT = 0xD1B54A32D192ED03L;
    private static final long STRENGTH_VARIANT = 0x94D049BB133111EBL;
    private static final long BEND_VARIANT = 0xDB4F0B9175AE2165L;

    private final long worldSeed;
    private final long surfaceRevision;
    private final MacroGeophysicalField macroGeophysics;
    private final TerrainSurfaceDefinition definition;
    private final double regionalSpan;

    DeterministicContinuousTerrainSurface(
            long worldSeed,
            long surfaceRevision,
            MacroGeophysicalField macroGeophysics,
            TerrainSurfaceDefinition definition) {
        if (macroGeophysics == null || definition == null) {
            throw new IllegalArgumentException("macroGeophysics and definition must not be null");
        }
        this.worldSeed = worldSeed;
        this.surfaceRevision = surfaceRevision;
        this.macroGeophysics = macroGeophysics;
        this.definition = definition;
        this.regionalSpan = regionalSpan(definition.regionalReliefScale().value());
    }

    @Override
    public double surfaceZAt(long x, long y) {
        double relief = definition.reliefIntensity().value();
        double ruggedness = definition.regionalRuggedness().value();
        double plateauTendency = definition.plateauTendency().value();
        double macroElevation = macroGeophysics.elevationAt(x, y);
        double macroMagnitude = Math.abs(macroElevation);

        // Stage 5 remains the large-scale vertical support. A slightly steeper coastal transfer
        // gives the sea datum a readable margin while retaining a continuous approach to Z=0.
        double landFactor = smoothStep(-0.12d, 0.22d, macroElevation);
        double macroVerticalScale = lerp(2_350.0d, 1_750.0d, landFactor);
        double coastSteepening = lerp(
                1.20d,
                1.0d,
                smoothStep(0.0d, 0.20d, macroMagnitude));
        double macroZ = macroElevation
                * macroVerticalScale
                * coastSteepening
                * (0.86d + 0.14d * Math.sqrt(macroMagnitude));

        // A shallow submerged shelf avoids turning every ocean margin into the same smooth bowl.
        double shallowShelf = smoothStep(0.0d, 0.10d, -macroElevation)
                * (1.0d - smoothStep(0.10d, 0.32d, -macroElevation));
        macroZ -= shallowShelf * 110.0d;

        // Broad coordinate warping only bends regional structures; it does not introduce a new
        // authored frequency or a fine-scale noise layer.
        double warpSpan = regionalSpan * 4.2d;
        double warpAmplitude = regionalSpan * lerp(0.05d, 0.16d, ruggedness);
        double warpedX = x + gradientNoiseAt(x, y, warpSpan, WARP_X_SALT) * warpAmplitude;
        double warpedY = y + gradientNoiseAt(x, y, warpSpan, WARP_Y_SALT) * warpAmplitude;

        // Local relief fades out before the shared sea datum. This preserves coherent coastlines
        // while still allowing inland terrain to become rugged immediately beyond the margin.
        double landInterior = smoothStep(0.035d, 0.20d, macroElevation);
        double coastGuard = smoothStep(0.025d, 0.16d, macroMagnitude);
        double province = gradientNoiseAt(
                warpedX,
                warpedY,
                regionalSpan * 1.4d,
                PROVINCE_SALT);
        double tectonic = gradientNoiseAt(
                warpedX,
                warpedY,
                regionalSpan * 3.4d,
                TECTONIC_SALT);
        double tectonicGate = smoothStep(-0.30d, 0.52d, tectonic);

        // Sparse finite mountain systems replace the old infinite zero-crossing belts. Each range
        // is a deterministic bent two-segment feature living in a broad regional hash cell. Only a
        // fixed 3x3 neighbourhood can influence a query, so work stays bounded and order-free.
        double rangeSpan = regionalSpan * 3.2d;
        RangeInfluence range = mountainRangeInfluenceAt(
                warpedX,
                warpedY,
                rangeSpan,
                ruggedness);
        double mountainSystem = clamp(
                (range.core() * 0.72d + range.shoulder() * 0.40d)
                        * (0.50d + 0.50d * tectonicGate),
                0.0d,
                1.2d);
        double mountainUplift = mountainSystem
                * landInterior
                * coastGuard
                * 1_700.0d
                * relief
                * lerp(0.52d, 1.0d, ruggedness);

        // Broad provinces provide readable uplands, plateaus and lowlands between mountain systems
        // instead of leaving the continent as a flat plane decorated by isolated ridges.
        double upland = smoothStep(0.10d, 0.66d, province);
        double lowland = smoothStep(0.12d, 0.68d, -province);
        double plateauShape = upland * smoothStep(-0.30d, 0.45d, tectonic);
        double plateauOffset = plateauShape
                * (1.0d - 0.50d * clamp(mountainSystem, 0.0d, 1.0d))
                * landInterior
                * coastGuard
                * 650.0d
                * relief
                * plateauTendency;
        double lowlandOffset = -lowland
                * landInterior
                * coastGuard
                * 250.0d
                * relief;
        double rollingBase = (upland - 0.30d * lowland)
                * 190.0d
                * relief
                * (0.35d + 0.65d * ruggedness)
                * landInterior
                * coastGuard;

        // Nested subordinate relief ensures zooming into the same authoritative surface reveals new
        // causal structure rather than a magnified interpolation of the coarse view. The hard
        // minimum span remains 1024 future horizontal cells, far above one-block Z noise.
        double reliefActivity = clamp(
                0.38d + 0.62d * Math.max(mountainSystem, upland * 0.55d),
                0.0d,
                1.0d);
        double plateauFlattening = 1.0d
                - plateauTendency * plateauShape * 0.72d;

        double mediumSpan = Math.max(MIN_MEDIUM_SPAN, regionalSpan / 7.5d);
        double mediumRelief = gradientNoiseAt(
                warpedX,
                warpedY,
                mediumSpan,
                MEDIUM_RELIEF_SALT);
        double mediumOffset = mediumRelief
                * 230.0d
                * relief
                * lerp(0.48d, 1.0d, ruggedness)
                * reliefActivity
                * plateauFlattening
                * landInterior
                * coastGuard;

        double fineSpan = Math.max(MIN_FINE_SPAN, regionalSpan / 42.0d);
        double fineRelief = gradientNoiseAt(
                warpedX,
                warpedY,
                fineSpan,
                FINE_RELIEF_SALT);
        double fineOffset = fineRelief
                * 115.0d
                * relief
                * lerp(0.55d, 1.0d, ruggedness)
                * reliefActivity
                * plateauFlattening
                * landInterior
                * coastGuard;

        double microSpan = Math.max(MIN_MICRO_SPAN, fineSpan / 4.0d);
        double microRelief = gradientNoiseAt(
                warpedX,
                warpedY,
                microSpan,
                MICRO_RELIEF_SALT);
        double microOffset = microRelief
                * 42.0d
                * relief
                * lerp(0.60d, 1.0d, ruggedness)
                * reliefActivity
                * plateauFlattening
                * landInterior
                * coastGuard;

        // Submerged terrain receives its own broad structure. Stage 6 still describes one surface;
        // this is ocean-floor relief, not a second ocean mask or water simulation.
        double oceanInterior = smoothStep(0.04d, 0.24d, -macroElevation);
        double oceanBroad = gradientNoiseAt(
                warpedX,
                warpedY,
                regionalSpan * 1.7d,
                OCEAN_BROAD_SALT);
        double oceanMedium = gradientNoiseAt(
                warpedX,
                warpedY,
                Math.max(32_768.0d, regionalSpan / 4.8d),
                OCEAN_MEDIUM_SALT);
        double oceanOffset = (oceanBroad * 260.0d + oceanMedium * 90.0d)
                * relief
                * oceanInterior;

        double candidate = macroZ
                + mountainUplift
                + plateauOffset
                + lowlandOffset
                + rollingBase
                + mediumOffset
                + fineOffset
                + microOffset
                + oceanOffset;

        // Stage 6 is not allowed to punch a random water topology through Stage 5's macro coastline.
        // The floor/ceiling approaches zero continuously with macroZ and only acts if deformation
        // would otherwise cross the shared datum.
        if (macroElevation > 0.0d) {
            candidate = Math.max(candidate, Math.min(4.0d, macroZ));
        } else if (macroElevation < 0.0d) {
            candidate = Math.min(candidate, Math.max(-4.0d, macroZ));
        }

        return clamp(candidate, MIN_SURFACE_Z, MAX_SURFACE_Z);
    }

    private RangeInfluence mountainRangeInfluenceAt(
            double x,
            double y,
            double span,
            double ruggedness) {
        long cellX = (long) Math.floor(x / span);
        long cellY = (long) Math.floor(y / span);
        double bestCore = 0.0d;
        double bestShoulder = 0.0d;

        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                long rangeCellX = cellX + offsetX;
                long rangeCellY = cellY + offsetY;
                long baseHash = latticeHash(
                        rangeCellX,
                        rangeCellY,
                        MOUNTAIN_RANGE_SALT);
                if (unitDouble(hashVariant(baseHash, ACTIVE_VARIANT)) <= 0.67d) {
                    continue;
                }

                double centerX = (rangeCellX
                                + 0.18d
                                + 0.64d * unitDouble(hashVariant(baseHash, CENTER_X_VARIANT)))
                        * span;
                double centerY = (rangeCellY
                                + 0.18d
                                + 0.64d * unitDouble(hashVariant(baseHash, CENTER_Y_VARIANT)))
                        * span;
                double angle = unitDouble(hashVariant(baseHash, ANGLE_VARIANT)) * Math.PI;
                double halfLength = span
                        * (0.30d
                                + 0.24d * unitDouble(hashVariant(baseHash, LENGTH_VARIANT)));
                double width = span
                        * (0.065d
                                + 0.055d * unitDouble(hashVariant(baseHash, WIDTH_VARIANT)))
                        * (1.12d - 0.28d * ruggedness);
                double directionX = Math.cos(angle);
                double directionY = Math.sin(angle);
                double endOffsetX = directionX * halfLength;
                double endOffsetY = directionY * halfLength;
                double ax = centerX - endOffsetX;
                double ay = centerY - endOffsetY;
                double bx = centerX + endOffsetX;
                double by = centerY + endOffsetY;

                double bend = (unitDouble(hashVariant(baseHash, BEND_VARIANT)) - 0.5d)
                        * 0.50d
                        * halfLength;
                double midX = centerX - directionY * bend;
                double midY = centerY + directionX * bend;

                double distance = Math.min(
                        segmentDistance(x, y, ax, ay, midX, midY),
                        segmentDistance(x, y, midX, midY, bx, by));
                double core = 1.0d - smoothStep(0.0d, width, distance);
                double shoulder = 1.0d - smoothStep(width, width * 2.8d, distance);
                double strength = 0.62d
                        + 0.38d * unitDouble(hashVariant(baseHash, STRENGTH_VARIANT));
                bestCore = Math.max(bestCore, core * strength);
                bestShoulder = Math.max(bestShoulder, shoulder * strength);
            }
        }

        return new RangeInfluence(bestCore, bestShoulder);
    }

    private double gradientNoiseAt(double x, double y, double span, long salt) {
        double gridX = x / span;
        double gridY = y / span;
        long cellX = (long) Math.floor(gridX);
        long cellY = (long) Math.floor(gridY);
        double localX = gridX - cellX;
        double localY = gridY - cellY;
        double blendX = smooth(localX);
        double blendY = smooth(localY);

        double n00 = gradientDot(cellX, cellY, localX, localY, salt);
        double n10 = gradientDot(cellX + 1L, cellY, localX - 1.0d, localY, salt);
        double n01 = gradientDot(cellX, cellY + 1L, localX, localY - 1.0d, salt);
        double n11 = gradientDot(cellX + 1L, cellY + 1L, localX - 1.0d, localY - 1.0d, salt);

        double lower = lerp(n00, n10, blendX);
        double upper = lerp(n01, n11, blendX);
        return clamp(lerp(lower, upper, blendY) * 1.55d, -1.0d, 1.0d);
    }

    private double gradientDot(
            long cellX,
            long cellY,
            double offsetX,
            double offsetY,
            long salt) {
        int direction = (int) (latticeHash(cellX, cellY, salt) & 7L);
        return switch (direction) {
            case 0 -> offsetX;
            case 1 -> -offsetX;
            case 2 -> offsetY;
            case 3 -> -offsetY;
            case 4 -> (offsetX + offsetY) * INV_SQRT_2;
            case 5 -> (-offsetX + offsetY) * INV_SQRT_2;
            case 6 -> (offsetX - offsetY) * INV_SQRT_2;
            default -> (-offsetX - offsetY) * INV_SQRT_2;
        };
    }

    private long latticeHash(long cellX, long cellY, long salt) {
        long value = mix64(worldSeed ^ salt);
        value = mix64(value ^ mix64(cellX));
        value = mix64(value ^ Long.rotateLeft(mix64(cellY), 29));
        return mix64(value ^ surfaceRevision);
    }

    private static long hashVariant(long baseHash, long variant) {
        return mix64(baseHash ^ variant);
    }

    private static double unitDouble(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static double segmentDistance(
            double x,
            double y,
            double ax,
            double ay,
            double bx,
            double by) {
        double vx = bx - ax;
        double vy = by - ay;
        double wx = x - ax;
        double wy = y - ay;
        double denominator = vx * vx + vy * vy;
        double amount = denominator == 0.0d
                ? 0.0d
                : clamp((wx * vx + wy * vy) / denominator, 0.0d, 1.0d);
        double nearestX = ax + amount * vx;
        double nearestY = ay + amount * vy;
        return Math.hypot(x - nearestX, y - nearestY);
    }

    private static double regionalSpan(double scale) {
        double ratio = MAX_REGIONAL_SPAN / (double) MIN_REGIONAL_SPAN;
        return MIN_REGIONAL_SPAN * Math.pow(ratio, scale);
    }

    private static double smoothStep(double edge0, double edge1, double value) {
        double amount = clamp((value - edge0) / (edge1 - edge0), 0.0d, 1.0d);
        return amount * amount * (3.0d - 2.0d * amount);
    }

    private static double smooth(double value) {
        return value * value * value * (value * (value * 6.0d - 15.0d) + 10.0d);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record RangeInfluence(double core, double shoulder) {}
}
