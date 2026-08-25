package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalField;

/**
 * Bounded deterministic Stage 6 surface model hidden behind {@link TerrainSurfaceEvolution}.
 *
 * <p>The model is intentionally hierarchical. Stage 5 supplies world-scale vertical support;
 * broad structural fields create regional provinces; smooth zero-crossing belts concentrate
 * orogenic/rift relief; province interiors create plateaus/lowlands; and smaller relief is allowed
 * only as a subordinate deformation of those larger structures. The smallest structural span is
 * thousands of future horizontal cells, so the source cannot contain block-scale Z ripple.</p>
 */
final class DeterministicContinuousTerrainSurface implements ContinuousTerrainSurface {
    private static final long MIN_REGIONAL_SPAN = 1L << 18;
    private static final long MAX_REGIONAL_SPAN = 1L << 20;
    private static final long MIN_DETAIL_SPAN = 1L << 13;
    private static final double INV_SQRT_2 = 0.7071067811865476d;
    private static final double MIN_SURFACE_Z = -4_096.0d;
    private static final double MAX_SURFACE_Z = 4_096.0d;

    private static final long WARP_X_SALT = 0x6D3A9F21C7B45E10L;
    private static final long WARP_Y_SALT = 0xA4E17C305BD268F9L;
    private static final long PROVINCE_SALT = 0x39C7D5A16E824BF0L;
    private static final long STRUCTURE_SALT = 0xD2B9186F43A05CE7L;
    private static final long REGIME_SALT = 0x71F4C30DA9B5628EL;
    private static final long MEDIUM_RELIEF_SALT = 0xC5A2703E19D8F64BL;
    private static final long DETAIL_RELIEF_SALT = 0x2E8F41B7D356A09CL;

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

        // Stage 5 remains the world-scale vertical support. Ocean basins are intentionally deeper
        // than continental support is high, with a smooth transition around the shared sea datum.
        double landScale = smoothStep(-0.18d, 0.18d, macroElevation);
        double macroVerticalScale = lerp(2_200.0d, 1_800.0d, landScale);
        double macroZ = macroElevation
                * macroVerticalScale
                * (0.70d + 0.30d * Math.sqrt(Math.abs(macroElevation)));

        // Very broad warping bends structural belts without introducing a new small spatial scale.
        double warpSpan = regionalSpan * 2.6d;
        double warpAmplitude = regionalSpan * lerp(0.06d, 0.18d, ruggedness);
        double warpedX = x + gradientNoiseAt(x, y, warpSpan, WARP_X_SALT) * warpAmplitude;
        double warpedY = y + gradientNoiseAt(x, y, warpSpan, WARP_Y_SALT) * warpAmplitude;

        // Smooth regional fields are interpreted structurally instead of simply being summed as
        // octaves. Zero-crossing bands form elongated province boundaries; a broader regime field
        // decides whether those boundaries tend toward uplift or extension.
        double province = gradientNoiseAt(warpedX, warpedY, regionalSpan, PROVINCE_SALT);
        double structural = gradientNoiseAt(
                warpedX,
                warpedY,
                regionalSpan * 0.72d,
                STRUCTURE_SALT);
        double regime = gradientNoiseAt(
                warpedX,
                warpedY,
                regionalSpan * 1.65d,
                REGIME_SALT);

        double belt = 1.0d - smoothStep(0.055d, 0.30d, Math.abs(structural));
        double provinceInterior = smoothStep(0.20d, 0.58d, Math.abs(structural));
        double compression = smoothStep(-0.20d, 0.52d, regime);
        double landContext = smoothStep(-0.10d, 0.22d, macroElevation);
        double oceanContext = 1.0d - smoothStep(-0.42d, 0.02d, macroElevation);
        double positiveProvince = smoothStep(0.03d, 0.68d, province);
        double negativeProvince = smoothStep(0.03d, 0.68d, -province);

        // Orogenic relief is concentrated in compressive structural belts; extensional parts of
        // the same broad boundary network become rifts/depressions rather than another ridge map.
        double ridge = belt
                * compression
                * landContext
                * 1_200.0d
                * relief
                * lerp(0.42d, 1.0d, ruggedness);
        double rift = -belt
                * (1.0d - compression)
                * landContext
                * 420.0d
                * relief
                * lerp(0.55d, 1.0d, ruggedness);

        // Province interiors express broad high surfaces and lowlands. Plateau tendency also
        // suppresses subordinate relief inside plateaus so they remain spatially readable.
        double plateau = positiveProvince
                * provinceInterior
                * landContext
                * 720.0d
                * relief
                * plateauTendency;
        double lowland = -negativeProvince
                * provinceInterior
                * landContext
                * 360.0d
                * relief;
        double oceanFloor = province * oceanContext * 230.0d * relief;

        double uplandContext = clamp(
                0.10d * landContext
                        + 0.75d * belt
                        + 0.45d * positiveProvince * provinceInterior,
                0.0d,
                1.0d);
        double plateauFlattening = 1.0d
                - plateauTendency * positiveProvince * provinceInterior * 0.60d;

        // Medium and fine deformation are deliberately subordinate to the structural context. The
        // hard minimum span is far above one future block and is private model policy rather than
        // an authored knob.
        double mediumRelief = gradientNoiseAt(
                warpedX,
                warpedY,
                regionalSpan / 5.5d,
                MEDIUM_RELIEF_SALT);
        double mediumOffset = mediumRelief
                * 220.0d
                * relief
                * ruggedness
                * uplandContext
                * plateauFlattening;

        double detailSpan = Math.max(
                MIN_DETAIL_SPAN,
                regionalSpan / lerp(18.0d, 28.0d, ruggedness));
        double detailRelief = gradientNoiseAt(
                warpedX,
                warpedY,
                detailSpan,
                DETAIL_RELIEF_SALT);
        double detailOffset = detailRelief
                * 95.0d
                * relief
                * ruggedness
                * uplandContext
                * plateauFlattening;

        return clamp(
                macroZ + ridge + rift + plateau + lowland + oceanFloor + mediumOffset + detailOffset,
                MIN_SURFACE_Z,
                MAX_SURFACE_Z);
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
}
