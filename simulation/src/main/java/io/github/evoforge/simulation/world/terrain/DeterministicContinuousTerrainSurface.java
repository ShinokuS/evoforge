package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalField;

/**
 * Bounded deterministic Stage 6 continuous Terrain surface.
 *
 * <p>Stage 5 remains the macro geography. Stage 6 refines only the coastal transition and develops
 * inland relief. Coastline detail comes from bounded coordinate warping of the Stage 5 field rather
 * than additive sign noise, so the shore stays coherent instead of becoming a noisy threshold.
 * Mountain systems are finite regions assembled from overlapping massifs; multi-scale ridged relief
 * then cuts ranges, shoulders, saddles and peaks inside those regions. No infinite contour-ridge
 * network participates in the surface.</p>
 *
 * <p>Every query performs fixed local deterministic work. Camera, page, tile, cache and request order
 * never participate in Terrain truth.</p>
 */
final class DeterministicContinuousTerrainSurface implements ContinuousTerrainSurface {
    private static final long MIN_REGIONAL_SPAN = 1L << 18;
    private static final long MAX_REGIONAL_SPAN = 1L << 20;
    private static final double MIN_SURFACE_Z = -4_096.0d;
    private static final double MAX_SURFACE_Z = 4_096.0d;
    private static final double INV_SQRT_2 = 0.7071067811865476d;

    private static final long COAST_X_BROAD_SALT = 0xA7B4C19D3E6205F1L;
    private static final long COAST_Y_BROAD_SALT = 0x19D6E84A52B703C1L;
    private static final long COAST_X_MEDIUM_SALT = 0x8C31E6B5A70D249FL;
    private static final long COAST_Y_MEDIUM_SALT = 0xC4D2E1F05A173B69L;
    private static final long COAST_X_FINE_SALT = 0x7419A0D5E236BC8FL;
    private static final long COAST_Y_FINE_SALT = 0x29D1C7A4E8530B6FL;

    private static final long PROVINCE_SALT = 0xD8047E31A5B269CFL;
    private static final long ROLLING_SALT = 0x739E25A4C1D86B0FL;
    private static final long HILL_SALT = 0x0D54A7B9E31C682FL;
    private static final long FINE_RELIEF_SALT = 0xB6F10C5D29A874E3L;
    private static final long MICRO_RELIEF_SALT = 0x3A71D6B5E92C408FL;
    private static final long OCEAN_BROAD_SALT = 0x5F28C1A96B73D40EL;
    private static final long OCEAN_FINE_SALT = 0xD6A8F241B30C597EL;

    private static final long SYSTEM_SALT = 0x91E64B2AC7D83510L;
    private static final long RIDGE_WARP_X_SALT = 0x4C9A731DB8E2056FL;
    private static final long RIDGE_WARP_Y_SALT = 0xC8047E31B5A269DFL;
    private static final long RIDGE_BROAD_SALT = 0x739E25B4C1D86A0FL;
    private static final long RIDGE_MEDIUM_SALT = 0x0D54A7C9E31B682FL;
    private static final long RIDGE_FINE_SALT = 0xB6F10C4D29A875E3L;
    private static final long PEAK_SALT = 0x3A71D6C5E92B408FL;

    private static final long SYSTEM_ACTIVE_VARIANT = 0x9E3779B97F4A7C15L;
    private static final long SYSTEM_CENTER_X_VARIANT = 0xA24BAED4963EE407L;
    private static final long SYSTEM_CENTER_Y_VARIANT = 0x9FB21C651E98DF25L;
    private static final long SYSTEM_ANGLE_VARIANT = 0xC13FA9A902A6328FL;
    private static final long SYSTEM_LENGTH_VARIANT = 0x91E10DA5C79E7B1DL;
    private static final long SYSTEM_WIDTH_VARIANT = 0xD1B54A32D192ED03L;
    private static final long SYSTEM_CURVE_VARIANT = 0xDB4F0B9175AE2165L;
    private static final long SYSTEM_STRENGTH_VARIANT = 0x94D049BB133111EBL;
    private static final long SYSTEM_BRANCH_VARIANT = 0x369DEA0F31A53F85L;
    private static final long MASSIF_ALONG_VARIANT = 0xDBA5B4E8C173092FL;
    private static final long MASSIF_SIDE_VARIANT = 0xA0F2EC75A1FE1575L;
    private static final long MASSIF_RADIUS_X_VARIANT = 0x89E182857D9ED689L;
    private static final long MASSIF_RADIUS_Y_VARIANT = 0xC6BC279692B5CC83L;
    private static final long MASSIF_STRENGTH_VARIANT = 0x632BE59BD9B4E019L;
    private static final long MASSIF_PEAK_VARIANT = 0x8CB92BA72F3D8DD7L;
    private static final long PEAK_X_VARIANT = 0xA3B195354A39B70DL;
    private static final long PEAK_Y_VARIANT = 0xF1BBCDCBFA53E0A7L;
    private static final long PEAK_RADIUS_VARIANT = 0xC2B2AE3D27D4EB4FL;
    private static final long PEAK_STRENGTH_VARIANT = 0x165667B19E3779F9L;

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
        double refinedMacro = refinedCoastalMacroAt(x, y, macroElevation, ruggedness);
        double macroVerticalScale = lerp(2_500.0d, 1_850.0d, smoothStep(-0.10d, 0.28d, refinedMacro));
        double macroZ = signedPower(refinedMacro, 0.92d) * macroVerticalScale;

        double landInterior = smoothStep(0.040d, 0.20d, refinedMacro);
        double oceanInterior = smoothStep(0.045d, 0.24d, -refinedMacro);

        // Broad continental provinces create highlands, plateaus and lowlands independently from
        // mountain placement. They keep non-mountain interiors from becoming featureless planes.
        double province = gradientNoiseAt(x, y, regionalSpan * 1.65d, PROVINCE_SALT);
        double upland = smoothStep(0.02d, 0.68d, province);
        double lowland = smoothStep(0.08d, 0.70d, -province);
        double plateau = smoothStep(0.28d, 0.82d, province) * plateauTendency;
        double provinceOffset = (upland * 300.0d - lowland * 190.0d)
                * relief
                * landInterior;
        double plateauOffset = plateau
                * 360.0d
                * relief
                * landInterior;

        // Finite mountain systems define where mountains exist. Smooth envelope height is kept
        // deliberately modest; most visual height comes from nested ridged relief inside the area.
        MountainInfluence mountains = mountainInfluenceAt(x, y, ruggedness);
        double mountainActivity = smoothStep(0.025d, 0.78d, mountains.envelope()) * landInterior;
        double massifCore = smoothStep(0.05d, 0.90d, mountains.massif());
        double mountainBase = mountainActivity
                * (170.0d + 260.0d * massifCore)
                * relief
                * lerp(0.72d, 1.08d, ruggedness);

        double ridgeWarpSpan = Math.max(32_768.0d, regionalSpan / 6.5d);
        double ridgeWarpAmount = ridgeWarpSpan * 0.23d;
        double ridgeX = x + gradientNoiseAt(x, y, ridgeWarpSpan * 1.9d, RIDGE_WARP_X_SALT) * ridgeWarpAmount;
        double ridgeY = y + gradientNoiseAt(x, y, ridgeWarpSpan * 1.9d, RIDGE_WARP_Y_SALT) * ridgeWarpAmount;

        double ridgeBroad = rotatedRidgedNoiseAt(
                ridgeX,
                ridgeY,
                Math.max(18_432.0d, regionalSpan / 22.0d),
                RIDGE_BROAD_SALT,
                0.37d);
        double ridgeMedium = rotatedRidgedNoiseAt(
                ridgeX,
                ridgeY,
                Math.max(6_144.0d, regionalSpan / 72.0d),
                RIDGE_MEDIUM_SALT,
                1.21d);
        double ridgeFine = rotatedRidgedNoiseAt(
                ridgeX,
                ridgeY,
                Math.max(1_536.0d, regionalSpan / 280.0d),
                RIDGE_FINE_SALT,
                2.18d);
        double localPeaks = peakFieldAt(x, y, Math.max(3_072.0d, regionalSpan / 150.0d));

        double mountainTexture = mountainActivity
                * (ridgeBroad * 610.0d
                        + ridgeMedium * 430.0d
                        + ridgeFine * 215.0d
                        + localPeaks * 360.0d * (0.35d + 0.65d * massifCore))
                * relief
                * lerp(0.58d, 1.04d, ruggedness);

        // Non-mountain relief stays multi-scale, but amplitudes decrease with scale so the source
        // remains continuous and does not predict one-cell voxel chatter later in Stage 10.
        double plateauFlattening = 1.0d - plateau * 0.74d;
        double rolling = gradientNoiseAt(x, y, Math.max(40_960.0d, regionalSpan / 5.0d), ROLLING_SALT);
        double hills = gradientNoiseAt(x, y, Math.max(12_288.0d, regionalSpan / 17.0d), HILL_SALT);
        double fine = gradientNoiseAt(x, y, Math.max(3_072.0d, regionalSpan / 72.0d), FINE_RELIEF_SALT);
        double micro = gradientNoiseAt(x, y, Math.max(768.0d, regionalSpan / 720.0d), MICRO_RELIEF_SALT);
        double rollingOffset = (rolling * 150.0d + hills * 92.0d + fine * 44.0d + micro * 14.0d)
                * relief
                * lerp(0.34d, 1.0d, ruggedness)
                * plateauFlattening
                * landInterior
                * (1.0d - mountainActivity * 0.42d);

        // One continuous surface continues below sea level. Bathymetry stays broad; shoreline
        // complexity is produced by the warped macro coast, not by noisy sea-floor sign changes.
        double oceanBroad = gradientNoiseAt(x, y, regionalSpan * 1.9d, OCEAN_BROAD_SALT);
        double oceanFine = gradientNoiseAt(x, y, Math.max(57_344.0d, regionalSpan / 3.4d), OCEAN_FINE_SALT);
        double oceanOffset = (oceanBroad * 235.0d + oceanFine * 68.0d)
                * relief
                * oceanInterior;

        return clamp(
                macroZ
                        + provinceOffset
                        + plateauOffset
                        + mountainBase
                        + mountainTexture
                        + rollingOffset
                        + oceanOffset,
                MIN_SURFACE_Z,
                MAX_SURFACE_Z);
    }

    private double refinedCoastalMacroAt(long x, long y, double macroElevation, double ruggedness) {
        double coastalBand = 1.0d - smoothStep(0.025d, 0.23d, Math.abs(macroElevation));
        if (coastalBand <= 0.0d) return macroElevation;

        // Coordinate displacement, not additive height noise: the Stage-5 contour is bent by a
        // bounded amount at three descending scales. This produces coherent bays/capes without the
        // shredded threshold produced by adding independent fine noise directly to Z.
        double character = 0.72d + 0.28d * ruggedness;
        double warpX = gradientNoiseAt(x, y, 280_000.0d, COAST_X_BROAD_SALT) * 20_000.0d
                + gradientNoiseAt(x, y, 88_000.0d, COAST_X_MEDIUM_SALT) * 5_200.0d
                + gradientNoiseAt(x, y, 24_000.0d, COAST_X_FINE_SALT) * 1_250.0d;
        double warpY = gradientNoiseAt(x, y, 280_000.0d, COAST_Y_BROAD_SALT) * 20_000.0d
                + gradientNoiseAt(x, y, 88_000.0d, COAST_Y_MEDIUM_SALT) * 5_200.0d
                + gradientNoiseAt(x, y, 24_000.0d, COAST_Y_FINE_SALT) * 1_250.0d;
        long warpedX = Math.round(x + warpX * character);
        long warpedY = Math.round(y + warpY * character);
        double warpedMacro = macroGeophysics.elevationAt(warpedX, warpedY);
        return lerp(macroElevation, warpedMacro, coastalBand * 0.92d);
    }

    private MountainInfluence mountainInfluenceAt(double x, double y, double ruggedness) {
        double systemSpan = regionalSpan * 2.65d;
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
                if (active < lerp(0.72d, 0.56d, ruggedness)) continue;

                double centerX = (systemCellX
                                + 0.16d
                                + unitDouble(hashVariant(baseHash, SYSTEM_CENTER_X_VARIANT)) * 0.68d)
                        * systemSpan;
                double centerY = (systemCellY
                                + 0.16d
                                + unitDouble(hashVariant(baseHash, SYSTEM_CENTER_Y_VARIANT)) * 0.68d)
                        * systemSpan;
                double angle = unitDouble(hashVariant(baseHash, SYSTEM_ANGLE_VARIANT)) * Math.PI;
                double dirX = Math.cos(angle);
                double dirY = Math.sin(angle);
                double sideX = -dirY;
                double sideY = dirX;
                double halfLength = systemSpan
                        * lerp(0.30d, 0.50d, unitDouble(hashVariant(baseHash, SYSTEM_LENGTH_VARIANT)));
                double systemWidth = systemSpan
                        * lerp(0.070d, 0.125d, unitDouble(hashVariant(baseHash, SYSTEM_WIDTH_VARIANT)));
                double curvature = (unitDouble(hashVariant(baseHash, SYSTEM_CURVE_VARIANT)) - 0.5d)
                        * halfLength
                        * 0.42d;
                double systemStrength = lerp(
                        0.74d,
                        1.10d,
                        unitDouble(hashVariant(baseHash, SYSTEM_STRENGTH_VARIANT)));

                int massifCount = 5 + (int) Math.floorMod(baseHash >>> 7, 4L);
                for (int index = 0; index < massifCount; index++) {
                    long massifHash = mix64(baseHash ^ ((long) index * 0x9E3779B97F4A7C15L));
                    double t = -1.0d + (2.0d * index / (massifCount - 1.0d));
                    double alongJitter = (unitDouble(hashVariant(massifHash, MASSIF_ALONG_VARIANT)) - 0.5d)
                            * halfLength
                            * 0.22d;
                    double sideJitter = (unitDouble(hashVariant(massifHash, MASSIF_SIDE_VARIANT)) - 0.5d)
                            * systemWidth
                            * 1.35d;
                    double bend = curvature * Math.sin(t * Math.PI * 0.85d);
                    double massifCenterX = centerX
                            + dirX * (t * halfLength + alongJitter)
                            + sideX * (bend + sideJitter);
                    double massifCenterY = centerY
                            + dirY * (t * halfLength + alongJitter)
                            + sideY * (bend + sideJitter);
                    double radiusAlong = systemWidth
                            * lerp(1.00d, 1.70d, unitDouble(hashVariant(massifHash, MASSIF_RADIUS_X_VARIANT)));
                    double radiusSide = systemWidth
                            * lerp(0.74d, 1.18d, unitDouble(hashVariant(massifHash, MASSIF_RADIUS_Y_VARIANT)));
                    double strength = systemStrength
                            * lerp(0.74d, 1.08d, unitDouble(hashVariant(massifHash, MASSIF_STRENGTH_VARIANT)));

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
                            radiusAlong * 2.25d,
                            radiusSide * 2.35d);
                    bestMassif = Math.max(bestMassif, massif * strength);
                    bestEnvelope = Math.max(bestEnvelope, shoulder * strength * 0.78d);

                    if (unitDouble(hashVariant(massifHash, SYSTEM_BRANCH_VARIANT)) > 0.66d) {
                        double branchSide = unitDouble(hashVariant(massifHash, MASSIF_PEAK_VARIANT)) > 0.5d ? 1.0d : -1.0d;
                        double branchX = massifCenterX + sideX * radiusSide * 1.30d * branchSide;
                        double branchY = massifCenterY + sideY * radiusSide * 1.30d * branchSide;
                        double branch = ellipticalBump(
                                x,
                                y,
                                branchX,
                                branchY,
                                sideX,
                                sideY,
                                radiusAlong * 0.72d,
                                radiusSide * 0.78d);
                        bestMassif = Math.max(bestMassif, branch * strength * 0.76d);
                        bestEnvelope = Math.max(bestEnvelope, branch * strength * 0.58d);
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
                        * lerp(0.30d, 0.58d, unitDouble(hashVariant(hash, PEAK_RADIUS_VARIANT)));
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

    private double rotatedRidgedNoiseAt(double x, double y, double span, long salt, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rotatedX = x * cos - y * sin;
        double rotatedY = x * sin + y * cos;
        return ridgedNoiseAt(rotatedX, rotatedY, span, salt);
    }

    private double ridgedNoiseAt(double x, double y, double span, long salt) {
        double raw = gradientNoiseAt(x, y, span, salt);
        double ridge = 1.0d - Math.abs(raw);
        ridge *= ridge;
        return smoothStep(0.18d, 0.94d, ridge);
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
