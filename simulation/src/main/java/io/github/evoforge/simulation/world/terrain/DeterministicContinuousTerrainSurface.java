package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalField;

/**
 * Bounded deterministic Stage 6 surface model hidden behind {@link TerrainSurfaceEvolution}.
 *
 * <p>Stage 5 remains the macro land/ocean support. Stage 6 refines that support with a narrow,
 * multi-scale coastal band and builds inland relief from finite mountain systems made of
 * overlapping massifs. Mountain systems are areas, not painted centre-lines: broad shoulders,
 * individual massif lobes, subordinate ridges and local peaks all contribute to one continuous
 * height field. This avoids both the smooth-coast/blurred-zoom failure mode and the worm-like ridge
 * networks produced by contour-based mountain masks.</p>
 *
 * <p>Every query examines only a fixed neighbourhood of deterministic hash cells. No world-sized
 * arrays, mutable caches or traversal-order state participate in terrain truth.</p>
 */
final class DeterministicContinuousTerrainSurface implements ContinuousTerrainSurface {
    private static final long MIN_REGIONAL_SPAN = 1L << 18;
    private static final long MAX_REGIONAL_SPAN = 1L << 20;
    private static final double MIN_SURFACE_Z = -4_096.0d;
    private static final double MAX_SURFACE_Z = 4_096.0d;
    private static final double INV_SQRT_2 = 0.7071067811865476d;

    private static final long COAST_WARP_X_SALT = 0xA7B4C19D3E6205F1L;
    private static final long COAST_WARP_Y_SALT = 0x19D6E84A52B703C1L;
    private static final long COAST_BROAD_SALT = 0x8C31E6B5A70D249FL;
    private static final long COAST_MEDIUM_SALT = 0xC4D2E1F05A173B69L;
    private static final long COAST_FINE_SALT = 0x7419A0D5E236BC8FL;
    private static final long COAST_MICRO_SALT = 0x29D1C7A4E8530B6FL;

    private static final long PROVINCE_SALT = 0xD8047E31A5B269CFL;
    private static final long ROLLING_SALT = 0x739E25A4C1D86B0FL;
    private static final long HILL_SALT = 0x0D54A7B9E31C682FL;
    private static final long FINE_RELIEF_SALT = 0xB6F10C5D29A874E3L;
    private static final long MICRO_RELIEF_SALT = 0x3A71D6B5E92C408FL;
    private static final long NANO_RELIEF_SALT = 0x8D25F0A143CE769BL;
    private static final long OCEAN_BROAD_SALT = 0x5F28C1A96B73D40EL;
    private static final long OCEAN_FINE_SALT = 0xD6A8F241B30C597EL;

    private static final long SYSTEM_SALT = 0x9E3779B97F4A7C15L;
    private static final long SYSTEM_ACTIVE_VARIANT = 0xA24BAED4963EE407L;
    private static final long SYSTEM_CENTER_X_VARIANT = 0x9FB21C651E98DF25L;
    private static final long SYSTEM_CENTER_Y_VARIANT = 0xC13FA9A902A6328FL;
    private static final long SYSTEM_ANGLE_VARIANT = 0x91E10DA5C79E7B1DL;
    private static final long SYSTEM_LENGTH_VARIANT = 0xD1B54A32D192ED03L;
    private static final long SYSTEM_WIDTH_VARIANT = 0x94D049BB133111EBL;
    private static final long SYSTEM_CURVE_VARIANT = 0xDB4F0B9175AE2165L;
    private static final long SYSTEM_STRENGTH_VARIANT = 0xBBE0563303A4615FL;
    private static final long SYSTEM_BRANCH_VARIANT = 0xE7037ED1A0B428DBL;

    private static final long MASSIF_ALONG_VARIANT = 0x8EBC6AF09C88C6E3L;
    private static final long MASSIF_SIDE_VARIANT = 0x589965CC75374CC3L;
    private static final long MASSIF_RADIUS_X_VARIANT = 0x1D8E4E27C47D124FL;
    private static final long MASSIF_RADIUS_Y_VARIANT = 0xEB44ACCAB455D165L;
    private static final long MASSIF_STRENGTH_VARIANT = 0x6E5B9D8A7C31F240L;
    private static final long MASSIF_PEAK_VARIANT = 0xF1357AEA2E62A9C5L;

    private static final long PEAK_SALT = 0xC6BC279692B5C323L;
    private static final long PEAK_X_VARIANT = 0xD2B74407B1CE6E93L;
    private static final long PEAK_Y_VARIANT = 0xCA5A826395121157L;
    private static final long PEAK_RADIUS_VARIANT = 0x9E3779B185EBCA87L;
    private static final long PEAK_STRENGTH_VARIANT = 0xA4093822299F31D0L;

    private static final long RIDGE_WARP_X_SALT = 0x082EFA98EC4E6C89L;
    private static final long RIDGE_WARP_Y_SALT = 0x452821E638D01377L;
    private static final long RIDGE_BROAD_SALT = 0xBE5466CF34E90C6CL;
    private static final long RIDGE_FINE_SALT = 0xC0AC29B7C97C50DDL;

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

        // Keep the macro signal broad, but do not freeze its zero contour. Stage 6 is allowed to
        // reshape only the narrow coastal band below, so Stage 5 remains the world-scale geography.
        double macroVerticalScale = lerp(2_450.0d, 1_850.0d, smoothStep(-0.10d, 0.25d, macroElevation));
        double macroZ = signedPower(macroElevation, 0.92d) * macroVerticalScale;

        // Multi-scale coastal refinement. The displacement is strongest very near the Stage-5 sea
        // crossing and fades rapidly inland/offshore, preventing long artificial peninsulas while
        // still producing bays, capes, coves and small near-shore islands.
        double coastWarpSpan = 360_000.0d;
        double coastWarp = 32_000.0d * (0.55d + 0.45d * ruggedness);
        double coastX = x + gradientNoiseAt(x, y, coastWarpSpan, COAST_WARP_X_SALT) * coastWarp;
        double coastY = y + gradientNoiseAt(x, y, coastWarpSpan, COAST_WARP_Y_SALT) * coastWarp;
        double coastalBand = 1.0d - smoothStep(0.015d, 0.20d, macroMagnitude);
        double coastalShape =
                gradientNoiseAt(coastX, coastY, 220_000.0d, COAST_BROAD_SALT) * 0.48d
                        + gradientNoiseAt(coastX, coastY, 64_000.0d, COAST_MEDIUM_SALT) * 0.28d
                        + gradientNoiseAt(coastX, coastY, 18_000.0d, COAST_FINE_SALT) * 0.16d
                        + gradientNoiseAt(coastX, coastY, 4_096.0d, COAST_MICRO_SALT) * 0.08d;
        double coastalOffset = coastalShape
                * coastalBand
                * 260.0d
                * (0.55d + 0.45d * ruggedness);

        double landInterior = smoothStep(0.045d, 0.22d, macroElevation);
        double oceanInterior = smoothStep(0.045d, 0.24d, -macroElevation);

        // Broad provinces determine whether inland terrain reads as lowland, rolling upland or
        // plateau. They are intentionally independent from mountain placement.
        double province = gradientNoiseAt(x, y, regionalSpan * 1.55d, PROVINCE_SALT);
        double upland = smoothStep(0.04d, 0.70d, province);
        double lowland = smoothStep(0.10d, 0.72d, -province);
        double plateau = smoothStep(0.22d, 0.78d, province) * plateauTendency;
        double provinceOffset = (upland * 320.0d - lowland * 180.0d)
                * relief
                * landInterior;
        double plateauOffset = plateau
                * 380.0d
                * relief
                * landInterior;

        // Finite mountain systems are built from overlapping area massifs. The envelope is broad
        // enough to read as a mountain region at world scale, while subordinate fields below reveal
        // peaks and ridges only as the same authoritative surface is sampled more finely.
        MountainInfluence mountains = mountainInfluenceAt(x, y, ruggedness);
        double mountainActivity = mountains.envelope() * landInterior;
        double mountainBase = mountainActivity
                * (760.0d + 660.0d * mountains.massif())
                * relief
                * lerp(0.62d, 1.05d, ruggedness);

        double ridgeWarpSpan = Math.max(24_576.0d, regionalSpan / 9.0d);
        double ridgeWarpAmount = ridgeWarpSpan * 0.34d;
        double ridgeX = x + gradientNoiseAt(x, y, ridgeWarpSpan * 2.4d, RIDGE_WARP_X_SALT) * ridgeWarpAmount;
        double ridgeY = y + gradientNoiseAt(x, y, ridgeWarpSpan * 2.4d, RIDGE_WARP_Y_SALT) * ridgeWarpAmount;
        double ridgeBroad = ridgedNoiseAt(ridgeX, ridgeY, Math.max(8_192.0d, regionalSpan / 20.0d), RIDGE_BROAD_SALT);
        double ridgeFine = ridgedNoiseAt(ridgeX, ridgeY, Math.max(2_048.0d, regionalSpan / 72.0d), RIDGE_FINE_SALT);
        double localPeaks = peakFieldAt(x, y, Math.max(6_144.0d, regionalSpan / 34.0d));
        double mountainTexture = mountainActivity
                * (ridgeBroad * 300.0d + ridgeFine * 125.0d + localPeaks * 620.0d * mountains.massif())
                * relief
                * lerp(0.55d, 1.0d, ruggedness);

        // Rolling relief keeps non-mountain interiors alive. Plateau areas deliberately attenuate
        // the smaller layers rather than becoming perfectly flat mathematical shelves.
        double plateauFlattening = 1.0d - plateau * 0.72d;
        double rolling = gradientNoiseAt(x, y, Math.max(32_768.0d, regionalSpan / 5.5d), ROLLING_SALT);
        double hills = gradientNoiseAt(x, y, Math.max(10_240.0d, regionalSpan / 18.0d), HILL_SALT);
        double fine = gradientNoiseAt(x, y, Math.max(2_048.0d, regionalSpan / 64.0d), FINE_RELIEF_SALT);
        double micro = gradientNoiseAt(x, y, Math.max(384.0d, regionalSpan / 768.0d), MICRO_RELIEF_SALT);
        double nano = gradientNoiseAt(x, y, Math.max(128.0d, regionalSpan / 4_096.0d), NANO_RELIEF_SALT);
        double rollingOffset = (rolling * 145.0d + hills * 82.0d + fine * 38.0d + micro * 12.0d + nano * 4.0d)
                * relief
                * lerp(0.35d, 1.0d, ruggedness)
                * plateauFlattening
                * landInterior
                * (1.0d - mountains.envelope() * 0.45d);

        // The same field continues beneath sea level. Ocean relief stays broad so the coastline is
        // not surrounded by a noisy bathymetric halo.
        double oceanBroad = gradientNoiseAt(x, y, regionalSpan * 1.8d, OCEAN_BROAD_SALT);
        double oceanFine = gradientNoiseAt(x, y, Math.max(49_152.0d, regionalSpan / 3.8d), OCEAN_FINE_SALT);
        double oceanOffset = (oceanBroad * 240.0d + oceanFine * 72.0d)
                * relief
                * oceanInterior;

        return clamp(
                macroZ
                        + coastalOffset
                        + provinceOffset
                        + plateauOffset
                        + mountainBase
                        + mountainTexture
                        + rollingOffset
                        + oceanOffset,
                MIN_SURFACE_Z,
                MAX_SURFACE_Z);
    }

    private MountainInfluence mountainInfluenceAt(double x, double y, double ruggedness) {
        double systemSpan = regionalSpan * 2.9d;
        long cellX = (long) Math.floor(x / systemSpan);
        long cellY = (long) Math.floor(y / systemSpan);
        double bestEnvelope = 0.0d;
        double bestMassif = 0.0d;

        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                long systemCellX = cellX + offsetX;
                long systemCellY = cellY + offsetY;
                long baseHash = latticeHash(systemCellX, systemCellY, SYSTEM_SALT);
                double active = unitDouble(hashVariant(baseHash, SYSTEM_ACTIVE_VARIANT));
                if (active < lerp(0.76d, 0.61d, ruggedness)) continue;

                double centerX = (systemCellX
                                + 0.18d
                                + unitDouble(hashVariant(baseHash, SYSTEM_CENTER_X_VARIANT)) * 0.64d)
                        * systemSpan;
                double centerY = (systemCellY
                                + 0.18d
                                + unitDouble(hashVariant(baseHash, SYSTEM_CENTER_Y_VARIANT)) * 0.64d)
                        * systemSpan;
                double angle = unitDouble(hashVariant(baseHash, SYSTEM_ANGLE_VARIANT)) * Math.PI;
                double dirX = Math.cos(angle);
                double dirY = Math.sin(angle);
                double sideX = -dirY;
                double sideY = dirX;
                double halfLength = systemSpan
                        * lerp(0.26d, 0.48d, unitDouble(hashVariant(baseHash, SYSTEM_LENGTH_VARIANT)));
                double systemWidth = systemSpan
                        * lerp(0.075d, 0.145d, unitDouble(hashVariant(baseHash, SYSTEM_WIDTH_VARIANT)));
                double curvature = (unitDouble(hashVariant(baseHash, SYSTEM_CURVE_VARIANT)) - 0.5d)
                        * halfLength
                        * 0.54d;
                double systemStrength = lerp(
                        0.70d,
                        1.08d,
                        unitDouble(hashVariant(baseHash, SYSTEM_STRENGTH_VARIANT)));

                int massifCount = 4 + (int) Math.floorMod(baseHash >>> 7, 3L);
                for (int index = 0; index < massifCount; index++) {
                    long massifHash = mix64(baseHash ^ ((long) index * 0x9E3779B97F4A7C15L));
                    double t = massifCount == 1
                            ? 0.0d
                            : -1.0d + (2.0d * index / (massifCount - 1.0d));
                    double alongJitter = (unitDouble(hashVariant(massifHash, MASSIF_ALONG_VARIANT)) - 0.5d)
                            * halfLength
                            * 0.28d;
                    double sideJitter = (unitDouble(hashVariant(massifHash, MASSIF_SIDE_VARIANT)) - 0.5d)
                            * systemWidth
                            * 1.15d;
                    double bend = curvature * (1.0d - t * t) * Math.copySign(1.0d, t == 0.0d ? 1.0d : t);
                    double massifCenterX = centerX
                            + dirX * (t * halfLength + alongJitter)
                            + sideX * (bend + sideJitter);
                    double massifCenterY = centerY
                            + dirY * (t * halfLength + alongJitter)
                            + sideY * (bend + sideJitter);
                    double radiusAlong = systemWidth
                            * lerp(0.95d, 1.65d, unitDouble(hashVariant(massifHash, MASSIF_RADIUS_X_VARIANT)));
                    double radiusSide = systemWidth
                            * lerp(0.72d, 1.28d, unitDouble(hashVariant(massifHash, MASSIF_RADIUS_Y_VARIANT)));
                    double strength = systemStrength
                            * lerp(0.72d, 1.08d, unitDouble(hashVariant(massifHash, MASSIF_STRENGTH_VARIANT)));
                    double massif = ellipticalBump(
                            x,
                            y,
                            massifCenterX,
                            massifCenterY,
                            dirX,
                            dirY,
                            radiusAlong,
                            radiusSide);
                    double shoulder = ellipticalBump(
                            x,
                            y,
                            massifCenterX,
                            massifCenterY,
                            dirX,
                            dirY,
                            radiusAlong * 2.15d,
                            radiusSide * 2.35d);
                    bestMassif = Math.max(bestMassif, massif * strength);
                    bestEnvelope = Math.max(bestEnvelope, shoulder * strength * 0.82d);

                    // A subset of massifs grows one short oblique lobe. This gives mountain systems
                    // natural forks and compact side ranges without tracing an infinite boundary.
                    if (unitDouble(hashVariant(massifHash, SYSTEM_BRANCH_VARIANT)) > 0.68d) {
                        double branchSide = unitDouble(hashVariant(massifHash, MASSIF_PEAK_VARIANT)) > 0.5d ? 1.0d : -1.0d;
                        double branchX = massifCenterX + sideX * radiusSide * 1.25d * branchSide;
                        double branchY = massifCenterY + sideY * radiusSide * 1.25d * branchSide;
                        double branch = ellipticalBump(
                                x,
                                y,
                                branchX,
                                branchY,
                                sideX,
                                sideY,
                                radiusAlong * 0.72d,
                                radiusSide * 0.82d);
                        bestMassif = Math.max(bestMassif, branch * strength * 0.78d);
                        bestEnvelope = Math.max(bestEnvelope, branch * strength * 0.66d);
                    }
                }
            }
        }

        return new MountainInfluence(
                clamp(bestEnvelope, 0.0d, 1.25d),
                clamp(bestMassif, 0.0d, 1.35d));
    }

    private double peakFieldAt(double x, double y, double span) {
        long cellX = (long) Math.floor(x / span);
        long cellY = (long) Math.floor(y / span);
        double best = 0.0d;
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                long peakCellX = cellX + offsetX;
                long peakCellY = cellY + offsetY;
                long hash = latticeHash(peakCellX, peakCellY, PEAK_SALT);
                double centerX = (peakCellX
                                + 0.12d
                                + 0.76d * unitDouble(hashVariant(hash, PEAK_X_VARIANT)))
                        * span;
                double centerY = (peakCellY
                                + 0.12d
                                + 0.76d * unitDouble(hashVariant(hash, PEAK_Y_VARIANT)))
                        * span;
                double radius = span
                        * lerp(0.28d, 0.62d, unitDouble(hashVariant(hash, PEAK_RADIUS_VARIANT)));
                double dx = x - centerX;
                double dy = y - centerY;
                double distance = Math.sqrt(dx * dx + dy * dy) / radius;
                double bump = 1.0d - smoothStep(0.0d, 1.0d, distance);
                double strength = lerp(0.55d, 1.0d, unitDouble(hashVariant(hash, PEAK_STRENGTH_VARIANT)));
                best = Math.max(best, bump * bump * strength);
            }
        }
        return best;
    }

    private static double ellipticalBump(
            double x,
            double y,
            double centerX,
            double centerY,
            double axisX,
            double axisY,
            double radiusAlong,
            double radiusSide) {
        double dx = x - centerX;
        double dy = y - centerY;
        double along = (dx * axisX + dy * axisY) / radiusAlong;
        double side = (-dx * axisY + dy * axisX) / radiusSide;
        double normalizedDistance = Math.sqrt(along * along + side * side);
        double bump = 1.0d - smoothStep(0.0d, 1.0d, normalizedDistance);
        return bump * bump * (3.0d - 2.0d * bump);
    }

    private double ridgedNoiseAt(double x, double y, double span, long salt) {
        double raw = gradientNoiseAt(x, y, span, salt);
        double ridge = 1.0d - Math.abs(raw);
        ridge *= ridge;
        // Remove the broad floor so only readable ridges remain; this is always masked by a finite
        // mountain-system envelope before reaching the final surface.
        return smoothStep(0.20d, 0.92d, ridge);
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

    private static double regionalSpan(double scale) {
        double ratio = MAX_REGIONAL_SPAN / (double) MIN_REGIONAL_SPAN;
        return MIN_REGIONAL_SPAN * Math.pow(ratio, scale);
    }

    private static double signedPower(double value, double exponent) {
        if (value == 0.0d) return 0.0d;
        return Math.copySign(Math.pow(Math.abs(value), exponent), value);
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

    private record MountainInfluence(double envelope, double massif) {}
}
