package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalField;

/**
 * Bounded deterministic Stage 6 continuous Terrain surface.
 *
 * <p>Stage 5 owns macro land/ocean geography. Stage 6 adds a hierarchy of coherent coastal
 * displacement, ordinary landforms and finite structural mountain systems. The authoritative
 * surface is coordinate-addressed and independent from pages, tiles and camera state.</p>
 *
 * <p>The same implementation also exposes a package-private scale-aware map projection. That
 * projection does not change Terrain truth: it only attenuates features that are too small for the
 * requested map sample interval, preventing unresolved exact detail from aliasing into coarse-map
 * pixel noise.</p>
 */
final class DeterministicContinuousTerrainSurface
        implements ContinuousTerrainSurface, TerrainSurfaceMapObservation {
    private static final long MIN_REGIONAL_SPAN = 1L << 18;
    private static final long MAX_REGIONAL_SPAN = 1L << 20;
    private static final double MIN_SURFACE_Z = -4_096.0d;
    private static final double MAX_SURFACE_Z = 4_096.0d;
    private static final double INV_SQRT_2 = 0.7071067811865476d;

    // Coast scales deliberately form a visible hierarchy. At a whole-world view only broad and
    // regional displacement survives map filtering; local/fine structure appears as zoom resolves it.
    private static final double COAST_BROAD_SPAN = 620_000.0d;
    private static final double COAST_REGIONAL_SPAN = 190_000.0d;
    private static final double COAST_LOCAL_SPAN = 58_000.0d;
    private static final double COAST_FINE_SPAN = 18_000.0d;
    private static final double COAST_BROAD_AMPLITUDE = 74_000.0d;
    private static final double COAST_REGIONAL_AMPLITUDE = 28_000.0d;
    private static final double COAST_LOCAL_AMPLITUDE = 8_500.0d;
    private static final double COAST_FINE_AMPLITUDE = 2_400.0d;

    private static final long COAST_X_BROAD_SALT = 0xA7B4C19D3E6205F1L;
    private static final long COAST_Y_BROAD_SALT = 0x19D6E84A52B703C1L;
    private static final long COAST_X_REGIONAL_SALT = 0x8C31E6B5A70D249FL;
    private static final long COAST_Y_REGIONAL_SALT = 0xC4D2E1F05A173B69L;
    private static final long COAST_X_LOCAL_SALT = 0x7419A0D5E236BC8FL;
    private static final long COAST_Y_LOCAL_SALT = 0x29D1C7A4E8530B6FL;
    private static final long COAST_X_FINE_SALT = 0xD08C31E6B5A70249L;
    private static final long COAST_Y_FINE_SALT = 0x69C4D2E1F05A173BL;

    private static final long PROVINCE_SALT = 0xD8047E31A5B269CFL;
    private static final long ROLLING_SALT = 0x739E25A4C1D86B0FL;
    private static final long FINE_RELIEF_SALT = 0xB6F10C5D29A874E3L;
    private static final long MICRO_RELIEF_SALT = 0x3A71D6B5E92C408FL;
    private static final long OCEAN_BROAD_SALT = 0x5F28C1A96B73D40EL;
    private static final long OCEAN_FINE_SALT = 0xD6A8F241B30C597EL;

    // Addressable V12-style explicit hill/depression feature lattice.
    private static final long LANDFORM_SALT = 0x42D95A7C13E6B80FL;
    private static final long LANDFORM_PATTERN_SALT = 0xA63D91E72C4B850FL;
    private static final long LANDFORM_CENTER_X_VARIANT = 0x9E3779B97F4A7C15L;
    private static final long LANDFORM_CENTER_Y_VARIANT = 0xA24BAED4963EE407L;
    private static final long LANDFORM_RADIUS_VARIANT = 0x9FB21C651E98DF25L;
    private static final long LANDFORM_STRENGTH_VARIANT = 0xC13FA9A902A6328FL;

    // V13-style finite structural mountain systems. The structural profiles own most mountain
    // height; no global ridged-noise network is painted over the land.
    private static final long SYSTEM_SALT = 0x91E64B2AC7D83510L;
    private static final long PEAK_SALT = 0x3A71D6C5E92B408FL;
    private static final long SYSTEM_ACTIVE_VARIANT = 0x91E10DA5C79E7B1DL;
    private static final long SYSTEM_CENTER_X_VARIANT = 0xD1B54A32D192ED03L;
    private static final long SYSTEM_CENTER_Y_VARIANT = 0xDB4F0B9175AE2165L;
    private static final long SYSTEM_ANGLE_VARIANT = 0x94D049BB133111EBL;
    private static final long SYSTEM_LENGTH_VARIANT = 0x369DEA0F31A53F85L;
    private static final long SYSTEM_WIDTH_VARIANT = 0xDBA5B4E8C173092FL;
    private static final long SYSTEM_CURVE_VARIANT = 0xA0F2EC75A1FE1575L;
    private static final long SYSTEM_STRENGTH_VARIANT = 0x89E182857D9ED689L;
    private static final long SYSTEM_LOBE_COUNT_VARIANT = 0xC6BC279692B5CC83L;
    private static final long LOBE_ALONG_VARIANT = 0x632BE59BD9B4E019L;
    private static final long LOBE_SIDE_VARIANT = 0x8CB92BA72F3D8DD7L;
    private static final long LOBE_LONG_NEG_VARIANT = 0xA3B195354A39B70DL;
    private static final long LOBE_LONG_POS_VARIANT = 0xF1BBCDCBFA53E0A7L;
    private static final long LOBE_WIDTH_LEFT_VARIANT = 0xC2B2AE3D27D4EB4FL;
    private static final long LOBE_WIDTH_RIGHT_VARIANT = 0x165667B19E3779F9L;
    private static final long LOBE_STRENGTH_VARIANT = 0x27D4EB2F165667C5L;
    private static final long LOBE_PLATEAU_VARIANT = 0x5D9B4E019632BE59L;
    private static final long PEAK_X_VARIANT = 0x7F4A7C159E3779B9L;
    private static final long PEAK_Y_VARIANT = 0x963EE407A24BAED4L;
    private static final long PEAK_RADIUS_VARIANT = 0x1E98DF259FB21C65L;
    private static final long PEAK_STRENGTH_VARIANT = 0x02A6328FC13FA9A9L;

    private final long worldSeed;
    private final long surfaceRevision;
    private final MacroGeophysicalField macroGeophysics;
    private final TerrainSurfaceDefinition definition;
    private final double regionalSpan;
    private final double landformSpan;

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
        this.landformSpan = lerp(48_000.0d, 96_000.0d, definition.regionalReliefScale().value());
    }

    @Override
    public double surfaceZAt(long x, long y) {
        return evaluate(x, y, 1L);
    }

    @Override
    public double surfaceZForMapAt(long x, long y, long sampleSpacing) {
        if (sampleSpacing <= 0L) throw new IllegalArgumentException("sampleSpacing must be > 0");
        return evaluate(x, y, sampleSpacing);
    }

    private double evaluate(long x, long y, long observationStep) {
        double relief = definition.reliefIntensity().value();
        double ruggedness = definition.regionalRuggedness().value();
        double plateauTendency = definition.plateauTendency().value();

        double macroElevation = macroGeophysics.elevationAt(x, y);
        double refinedMacro = refinedCoastalMacroAt(x, y, macroElevation, ruggedness, observationStep);
        double macroVerticalScale = lerp(2_500.0d, 1_850.0d, smoothStep(-0.10d, 0.28d, refinedMacro));
        double macroZ = signedPower(refinedMacro, 0.92d) * macroVerticalScale;

        double landInterior = smoothStep(0.038d, 0.19d, refinedMacro);
        double oceanInterior = smoothStep(0.045d, 0.24d, -refinedMacro);

        // Broad provinces organize continents before local landforms are added.
        double provinceSpan = regionalSpan * 1.70d;
        double province = gradientNoiseAt(x, y, provinceSpan, PROVINCE_SALT)
                * detailVisibility(provinceSpan, observationStep);
        double upland = smoothStep(0.03d, 0.70d, province);
        double lowland = smoothStep(0.10d, 0.72d, -province);
        double plateau = smoothStep(0.30d, 0.84d, province) * plateauTendency;
        double provinceOffset = (upland * 270.0d - lowland * 165.0d)
                * relief
                * landInterior;
        double plateauOffset = plateau * 330.0d * relief * landInterior;

        // Explicit local hills/depressions restore the accepted pre-Continuum idea that ordinary
        // terrain consists of spatial forms, not a uniform stack of high-frequency noise.
        double landformVisibility = detailVisibility(landformSpan, observationStep);
        double landforms = landformFieldAt(x, y, landformSpan) * landformVisibility;
        double landformOffset = landforms
                * 145.0d
                * relief
                * lerp(0.58d, 1.0d, ruggedness)
                * landInterior;

        MountainShape mountains = mountainShapeAt(x, y, ruggedness, plateauTendency);
        double mountainVisibility = detailVisibility(mountains.characteristicSpan(), observationStep);
        double mountainProfile = mountains.profile() * mountainVisibility * landInterior;
        double mountainOffset = mountainProfile
                * 1_620.0d
                * relief
                * mountains.strength()
                * lerp(0.72d, 1.08d, ruggedness);

        // Smaller peaks only become visible when the map can resolve them. They are masked by
        // structural mountain systems, so they cannot salt the rest of the continent with dots.
        double peakSpan = Math.max(18_000.0d, landformSpan * 0.36d);
        double peakVisibility = detailVisibility(peakSpan, observationStep);
        double localPeaks = peakVisibility == 0.0d
                ? 0.0d
                : peakFieldAt(x, y, peakSpan);
        double peakOffset = mountainProfile
                * localPeaks
                * 410.0d
                * relief
                * lerp(0.65d, 1.05d, ruggedness);

        // Subordinate ordinary relief. Each frequency is independently band-limited for map
        // observation, so a coarse tile never samples unresolved exact micro-relief as stipple.
        double plateauFlattening = 1.0d - plateau * 0.72d;
        double rollingSpan = Math.max(150_000.0d, landformSpan * 2.6d);
        double fineSpan = Math.max(12_000.0d, landformSpan * 0.25d);
        double microSpan = Math.max(3_072.0d, landformSpan / 24.0d);
        double rolling = gradientNoiseAt(x, y, rollingSpan, ROLLING_SALT)
                * detailVisibility(rollingSpan, observationStep);
        double fine = gradientNoiseAt(x, y, fineSpan, FINE_RELIEF_SALT)
                * detailVisibility(fineSpan, observationStep);
        double micro = gradientNoiseAt(x, y, microSpan, MICRO_RELIEF_SALT)
                * detailVisibility(microSpan, observationStep);
        double rollingOffset = (rolling * 88.0d + fine * 30.0d + micro * 14.0d)
                * relief
                * lerp(0.42d, 1.0d, ruggedness)
                * plateauFlattening
                * landInterior
                * (1.0d - mountainProfile * 0.62d);

        // Bathymetry remains broad and follows the same scale-aware observation rule.
        double oceanBroadSpan = regionalSpan * 1.95d;
        double oceanFineSpan = Math.max(62_000.0d, regionalSpan / 3.0d);
        double oceanBroad = gradientNoiseAt(x, y, oceanBroadSpan, OCEAN_BROAD_SALT)
                * detailVisibility(oceanBroadSpan, observationStep);
        double oceanFine = gradientNoiseAt(x, y, oceanFineSpan, OCEAN_FINE_SALT)
                * detailVisibility(oceanFineSpan, observationStep);
        double oceanOffset = (oceanBroad * 225.0d + oceanFine * 64.0d)
                * relief
                * oceanInterior;

        return clamp(
                macroZ
                        + provinceOffset
                        + plateauOffset
                        + landformOffset
                        + mountainOffset
                        + peakOffset
                        + rollingOffset
                        + oceanOffset,
                MIN_SURFACE_Z,
                MAX_SURFACE_Z);
    }

    private double refinedCoastalMacroAt(
            long x,
            long y,
            double macroElevation,
            double ruggedness,
            long observationStep) {
        double coastalBand = 1.0d - smoothStep(0.020d, 0.220d, Math.abs(macroElevation));
        if (coastalBand <= 0.0d) return macroElevation;

        double character = 0.84d + 0.16d * ruggedness;
        double warpX = coastLayer(x, y, COAST_BROAD_SPAN, COAST_BROAD_AMPLITUDE,
                        COAST_X_BROAD_SALT, observationStep)
                + coastLayer(x, y, COAST_REGIONAL_SPAN, COAST_REGIONAL_AMPLITUDE,
                        COAST_X_REGIONAL_SALT, observationStep)
                + coastLayer(x, y, COAST_LOCAL_SPAN, COAST_LOCAL_AMPLITUDE,
                        COAST_X_LOCAL_SALT, observationStep)
                + coastLayer(x, y, COAST_FINE_SPAN, COAST_FINE_AMPLITUDE,
                        COAST_X_FINE_SALT, observationStep);
        double warpY = coastLayer(x, y, COAST_BROAD_SPAN, COAST_BROAD_AMPLITUDE,
                        COAST_Y_BROAD_SALT, observationStep)
                + coastLayer(x, y, COAST_REGIONAL_SPAN, COAST_REGIONAL_AMPLITUDE,
                        COAST_Y_REGIONAL_SALT, observationStep)
                + coastLayer(x, y, COAST_LOCAL_SPAN, COAST_LOCAL_AMPLITUDE,
                        COAST_Y_LOCAL_SALT, observationStep)
                + coastLayer(x, y, COAST_FINE_SPAN, COAST_FINE_AMPLITUDE,
                        COAST_Y_FINE_SALT, observationStep);

        if (warpX == 0.0d && warpY == 0.0d) return macroElevation;
        long warpedX = Math.round(x + warpX * character);
        long warpedY = Math.round(y + warpY * character);
        double warpedMacro = macroGeophysics.elevationAt(warpedX, warpedY);
        return lerp(macroElevation, warpedMacro, coastalBand * 0.94d);
    }

    private double coastLayer(
            long x,
            long y,
            double span,
            double amplitude,
            long salt,
            long observationStep) {
        double visibility = detailVisibility(span, observationStep);
        if (visibility == 0.0d) return 0.0d;
        return gradientNoiseAt(x, y, span, salt) * amplitude * visibility;
    }

    private double landformFieldAt(double x, double y, double span) {
        long cellX = (long) Math.floor(x / span);
        long cellY = (long) Math.floor(y / span);
        double sum = 0.0d;

        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                long featureX = cellX + offsetX;
                long featureY = cellY + offsetY;
                long hash = latticeHash(featureX, featureY, LANDFORM_SALT);
                double centerX = (featureX
                                + 0.5d
                                + (unitDouble(hashVariant(hash, LANDFORM_CENTER_X_VARIANT)) - 0.5d) * 0.44d)
                        * span;
                double centerY = (featureY
                                + 0.5d
                                + (unitDouble(hashVariant(hash, LANDFORM_CENTER_Y_VARIANT)) - 0.5d) * 0.44d)
                        * span;
                double radius = span * lerp(
                        0.58d,
                        0.93d,
                        unitDouble(hashVariant(hash, LANDFORM_RADIUS_VARIANT)));
                double dx = x - centerX;
                double dy = y - centerY;
                double distance = Math.sqrt(dx * dx + dy * dy) / radius;
                if (distance >= 1.0d) continue;

                double bump = 1.0d - smoothStep(0.0d, 1.0d, distance);
                bump = bump * bump * (3.0d - 2.0d * bump);
                double magnitude = lerp(
                        0.58d,
                        1.0d,
                        unitDouble(hashVariant(hash, LANDFORM_STRENGTH_VARIANT)));
                sum += landformSign(featureX, featureY) * bump * magnitude;
            }
        }
        return clamp(sum, -1.35d, 1.35d);
    }

    /** Every deterministic 2x2 feature block contains hills and depressions without a global grid phase. */
    private double landformSign(long featureX, long featureY) {
        long blockX = Math.floorDiv(featureX, 2L);
        long blockY = Math.floorDiv(featureY, 2L);
        long blockHash = latticeHash(blockX, blockY, LANDFORM_PATTERN_SALT);
        long phase = unitDouble(blockHash) >= 0.5d ? 1L : 0L;
        return ((featureX + featureY + phase) & 1L) == 0L ? 1.0d : -1.0d;
    }

    private MountainShape mountainShapeAt(
            double x,
            double y,
            double ruggedness,
            double plateauTendency) {
        double systemSpan = regionalSpan * 3.45d;
        long cellX = (long) Math.floor(x / systemSpan);
        long cellY = (long) Math.floor(y / systemSpan);
        double bestProfile = 0.0d;
        double bestStrength = 1.0d;
        double bestCharacteristicSpan = systemSpan * 0.10d;

        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                long systemCellX = cellX + offsetX;
                long systemCellY = cellY + offsetY;
                long baseHash = latticeHash(systemCellX, systemCellY, SYSTEM_SALT);
                double active = unitDouble(hashVariant(baseHash, SYSTEM_ACTIVE_VARIANT));
                if (active < lerp(0.78d, 0.62d, ruggedness)) continue;

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
                double halfLength = systemSpan * lerp(
                        0.25d,
                        0.43d,
                        unitDouble(hashVariant(baseHash, SYSTEM_LENGTH_VARIANT)));
                double baseWidth = systemSpan * lerp(
                        0.040d,
                        0.072d,
                        unitDouble(hashVariant(baseHash, SYSTEM_WIDTH_VARIANT)));
                double curvature = (unitDouble(hashVariant(baseHash, SYSTEM_CURVE_VARIANT)) - 0.5d)
                        * baseWidth
                        * 2.8d;
                double systemStrength = lerp(
                        0.72d,
                        1.08d,
                        unitDouble(hashVariant(baseHash, SYSTEM_STRENGTH_VARIANT)));
                int lobeCount = 3 + (int) Math.floorMod(
                        hashVariant(baseHash, SYSTEM_LOBE_COUNT_VARIANT),
                        2L);

                for (int index = 0; index < lobeCount; index++) {
                    long lobeHash = mix64(baseHash ^ ((long) index * 0x9E3779B97F4A7C15L));
                    double t = lobeCount == 1
                            ? 0.0d
                            : -0.72d + 1.44d * index / (lobeCount - 1.0d);
                    double alongJitter = (unitDouble(hashVariant(lobeHash, LOBE_ALONG_VARIANT)) - 0.5d)
                            * halfLength
                            * 0.18d;
                    double sideJitter = (unitDouble(hashVariant(lobeHash, LOBE_SIDE_VARIANT)) - 0.5d)
                            * baseWidth
                            * 0.85d;
                    double bend = curvature * Math.sin(t * Math.PI * 0.92d);
                    double lobeCenterX = centerX
                            + dirX * (t * halfLength + alongJitter)
                            + sideX * (bend + sideJitter);
                    double lobeCenterY = centerY
                            + dirY * (t * halfLength + alongJitter)
                            + sideY * (bend + sideJitter);

                    double longBase = halfLength * lerp(
                            0.34d,
                            0.50d,
                            unitDouble(hashVariant(lobeHash, LOBE_LONG_POS_VARIANT)));
                    double negativeLong = longBase * lerp(
                            0.78d,
                            1.18d,
                            unitDouble(hashVariant(lobeHash, LOBE_LONG_NEG_VARIANT)));
                    double positiveLong = longBase * lerp(
                            0.78d,
                            1.18d,
                            unitDouble(hashVariant(lobeHash, LOBE_LONG_POS_VARIANT)));
                    double leftWidth = baseWidth * lerp(
                            0.76d,
                            1.20d,
                            unitDouble(hashVariant(lobeHash, LOBE_WIDTH_LEFT_VARIANT)));
                    double rightWidth = baseWidth * lerp(
                            0.76d,
                            1.20d,
                            unitDouble(hashVariant(lobeHash, LOBE_WIDTH_RIGHT_VARIANT)));
                    double lobeStrength = systemStrength * lerp(
                            0.78d,
                            1.08d,
                            unitDouble(hashVariant(lobeHash, LOBE_STRENGTH_VARIANT)));
                    boolean plateau = unitDouble(hashVariant(lobeHash, LOBE_PLATEAU_VARIANT))
                            < plateauTendency * 0.48d;

                    double profile = elongatedHillProfile(
                            x,
                            y,
                            lobeCenterX,
                            lobeCenterY,
                            dirX,
                            dirY,
                            negativeLong,
                            positiveLong,
                            leftWidth,
                            rightWidth,
                            plateau);
                    if (profile > bestProfile) {
                        bestProfile = profile;
                        bestStrength = lobeStrength;
                        bestCharacteristicSpan = Math.min(
                                Math.min(negativeLong, positiveLong),
                                Math.min(leftWidth, rightWidth)) * 2.0d;
                    }
                }
            }
        }

        return new MountainShape(
                clamp(bestProfile, 0.0d, 1.0d),
                bestStrength,
                Math.max(24_000.0d, bestCharacteristicSpan));
    }

    private static double elongatedHillProfile(
            double x,
            double y,
            double centerX,
            double centerY,
            double axisX,
            double axisY,
            double negativeLong,
            double positiveLong,
            double leftWidth,
            double rightWidth,
            boolean plateau) {
        double dx = x - centerX;
        double dy = y - centerY;
        double along = dx * axisX + dy * axisY;
        double across = -dx * axisY + dy * axisX;
        double longAxis = along < 0.0d ? negativeLong : positiveLong;
        double sideAxis = across < 0.0d ? leftWidth : rightWidth;
        if (longAxis <= 0.0d || sideAxis <= 0.0d) return 0.0d;

        double radius = Math.hypot(along / longAxis, across / sideAxis);
        if (radius >= 1.0d) return 0.0d;
        return layeredHill(radius, plateau);
    }

    /** Smooth summit, readable middle slope and smooth foot; optional small plateau core. */
    private static double layeredHill(double radius, boolean plateau) {
        double r = clamp(radius, 0.0d, 1.0d);
        if (plateau) {
            double core = 0.20d;
            if (r <= core) return 1.0d;
            r = (r - core) / (1.0d - core);
        }
        double summitEase = 0.16d;
        double footEase = 0.16d;
        double slope = 1.0d / (1.0d - (summitEase + footEase) * 0.5d);
        if (r < summitEase) {
            return Math.max(0.0d, 1.0d - slope * r * r / (2.0d * summitEase));
        }
        if (r <= 1.0d - footEase) {
            double summitValue = 1.0d - slope * summitEase * 0.5d;
            return Math.max(0.0d, summitValue - slope * (r - summitEase));
        }
        double remaining = 1.0d - r;
        return Math.max(0.0d, slope * remaining * remaining / (2.0d * footEase));
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
                                + 0.16d
                                + 0.68d * unitDouble(hashVariant(hash, PEAK_X_VARIANT)))
                        * span;
                double centerY = (peakCellY
                                + 0.16d
                                + 0.68d * unitDouble(hashVariant(hash, PEAK_Y_VARIANT)))
                        * span;
                double radius = span * lerp(
                        0.30d,
                        0.55d,
                        unitDouble(hashVariant(hash, PEAK_RADIUS_VARIANT)));
                double dx = x - centerX;
                double dy = y - centerY;
                double distance = Math.sqrt(dx * dx + dy * dy) / radius;
                if (distance >= 1.0d) continue;
                double bump = 1.0d - smoothStep(0.0d, 1.0d, distance);
                double strength = lerp(
                        0.55d,
                        1.0d,
                        unitDouble(hashVariant(hash, PEAK_STRENGTH_VARIANT)));
                best = Math.max(best, bump * bump * strength);
            }
        }
        return best;
    }

    /**
     * Visibility approaches one only when a feature has several samples across its characteristic
     * span. Features below roughly two samples are omitted from this derived observation instead of
     * being point-sampled into false coarse-map noise.
     */
    private static double detailVisibility(double featureSpan, long observationStep) {
        if (observationStep <= 1L) return 1.0d;
        double samplesAcross = featureSpan / observationStep;
        return smoothStep(1.65d, 4.75d, samplesAcross);
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

    private record MountainShape(double profile, double strength, double characteristicSpan) {}
}
