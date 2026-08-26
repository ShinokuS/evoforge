package io.github.evoforge.simulation.world.geophysics;

/**
 * Bounded deterministic Stage 5 macro-geophysical model hidden behind {@link MacroGeophysics}.
 *
 * <p>The accepted macro-elevation algorithm remains unchanged. A second structural capability is
 * reconstructed locally from the same world identity and authored definition so later terrain can
 * respond to coherent geophysical regions/boundaries instead of inventing major structures from
 * decorative noise. No full-world region raster/graph is stored.</p>
 */
final class DeterministicMacroGeophysicalField implements MacroGeophysicalModel {
    private static final long MIN_CONTINENT_SPAN = 1L << 20;
    private static final long MAX_CONTINENT_SPAN = 1L << 23;
    private static final long MIN_PROVINCE_SPAN = 1L << 18;
    private static final long MIN_STRUCTURE_SPAN = 1L << 19;
    private static final double INV_SQRT_2 = 0.7071067811865476d;
    private static final double STRUCTURE_JITTER = 0.28d;
    private static final double ACTIVE_BOUNDARY_INFLUENCE = 0.05d;

    private static final long CONTINENT_SALT = 0x5A17B4C39D2E61F0L;
    private static final long CONTINENT_SECONDARY_SALT = 0x19C6D87A42E5B301L;
    private static final long CONTINENT_TERTIARY_SALT = 0x8B31E6C5A70D249FL;
    private static final long PROVINCE_SALT = 0xC3D2E1F05A174B69L;
    private static final long ISLAND_ARC_SALT = 0x7419A0E5D236BC8FL;
    private static final long WARP_X_SALT = 0x29D1C7B4E8530A6FL;
    private static final long WARP_Y_SALT = 0xE4B68A172D95C30FL;

    private static final long STRUCTURE_JITTER_X_SALT = 0xA1C6E9B473205DF8L;
    private static final long STRUCTURE_JITTER_Y_SALT = 0x37B4D120EA968C5FL;
    private static final long STRUCTURE_REGION_ID_SALT = 0xC8F0731AB54E269DL;
    private static final long STRUCTURE_MOTION_ANGLE_SALT = 0x62DA9C31F4B785E0L;
    private static final long STRUCTURE_MOTION_SPEED_SALT = 0xE51B74C80A3926DFL;

    private final long seed;
    private final long revision;
    private final MacroGeophysicsDefinition definition;
    private final long continentSpan;
    private final double provinceSpan;
    private final double structureSpan;

    DeterministicMacroGeophysicalField(
            long seed,
            long revision,
            MacroGeophysicsDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        this.seed = seed;
        this.revision = revision;
        this.definition = definition;
        this.continentSpan = continentSpan(definition.continentalScale().value());

        // Fragmentation may shorten the regional structure scale, but it remains decisively macro.
        double provinceDivisor = lerp(2.6d, 4.4d, definition.fragmentation().value());
        this.provinceSpan = Math.max(MIN_PROVINCE_SPAN, continentSpan / provinceDivisor);

        // Structural regions are intentionally much broader than later Terrain landforms. More
        // fragmented macro worlds may contain more structural regions without turning them into
        // technical pages or fine sample-scale cells.
        double structureDivisor = lerp(1.65d, 2.85d, definition.fragmentation().value());
        this.structureSpan = Math.max(MIN_STRUCTURE_SPAN, continentSpan / structureDivisor);
    }

    long seed() {
        return seed;
    }

    long revision() {
        return revision;
    }

    MacroGeophysicsDefinition definition() {
        return definition;
    }

    @Override
    public double elevationAt(long x, long y) {
        double cohesion = definition.landmassCohesion().value();
        double fragmentation = definition.fragmentation().value();
        double variation = definition.macroVariation().value();

        // Very broad displacement removes obvious lattice alignment without adding fine detail.
        double warpSpan = continentSpan * 1.8d;
        double warpAmplitude = continentSpan * lerp(0.04d, 0.24d, variation);
        double warpedX = x + gradientNoiseAt(x, y, warpSpan, WARP_X_SALT) * warpAmplitude;
        double warpedY = y + gradientNoiseAt(x, y, warpSpan, WARP_Y_SALT) * warpAmplitude;

        double continentalSupport = continentalSupportAt(warpedX, warpedY);
        double stableContinentalSupport = stabilize(continentalSupport, cohesion);

        // Regional structure is strongest around continental margins. Deep continental interiors
        // and deep ocean basins therefore remain broad and readable rather than being perforated.
        double coastalInfluence = 1.0d
                - smoothStep(0.18d, 0.62d, Math.abs(stableContinentalSupport));
        double provinceSupport = gradientNoiseAt(warpedX, warpedY, provinceSpan, PROVINCE_SALT);
        double regionalWeight = lerp(0.035d, 0.225d, fragmentation);
        double support = stableContinentalSupport
                + provinceSupport * regionalWeight * coastalInfluence;

        // At high fragmentation, sinuous zero-crossings of a separate regional field can emerge as
        // island arcs inside the same coastal transition zone. This creates groups/chains rather
        // than uniformly increasing high-frequency noise across the entire world.
        double arcField = gradientNoiseAt(
                warpedX,
                warpedY,
                provinceSpan * 1.25d,
                ISLAND_ARC_SALT);
        double islandArc = 1.0d - smoothStep(0.08d, 0.36d, Math.abs(arcField));
        support += islandArc
                * fragmentation
                * fragmentation
                * 0.10d
                * coastalInfluence;

        // Ocean prevalence remains a tendency applied to the one authoritative elevation field.
        // The sea datum itself stays fixed at zero.
        double seaBias = (0.5d - definition.oceanPrevalence().value()) * 0.95d;
        return clamp(support + seaBias, -1.0d, 1.0d);
    }

    @Override
    public MacroGeophysicalStructure structureAt(long x, long y) {
        double cohesion = definition.landmassCohesion().value();
        double variation = definition.macroVariation().value();

        double warpSpan = continentSpan * 1.8d;
        double warpAmplitude = continentSpan * lerp(0.04d, 0.24d, variation);
        double warpedX = x + gradientNoiseAt(x, y, warpSpan, WARP_X_SALT) * warpAmplitude;
        double warpedY = y + gradientNoiseAt(x, y, warpSpan, WARP_Y_SALT) * warpAmplitude;

        double continentalSupport = stabilize(continentalSupportAt(warpedX, warpedY), cohesion);
        double marginInfluence = 1.0d
                - smoothStep(0.18d, 0.62d, Math.abs(continentalSupport));

        StructuralSitePair pair = nearestStructuralSites(warpedX, warpedY);
        StructuralSite primary = pair.primary();
        StructuralSite secondary = pair.secondary();
        double primaryDistance = Math.sqrt(pair.primaryDistanceSquared());
        double secondaryDistance = Math.sqrt(pair.secondaryDistanceSquared());
        double distanceGap = Math.max(0.0d, secondaryDistance - primaryDistance);
        double boundaryInfluence = 1.0d
                - smoothStep(0.0d, structureSpan * 0.22d, distanceGap);

        double normalX = secondary.centerX() - primary.centerX();
        double normalY = secondary.centerY() - primary.centerY();
        double inverseLength = 1.0d / Math.hypot(normalX, normalY);
        normalX *= inverseLength;
        normalY *= inverseLength;

        Motion primaryMotion = motionFor(primary.cellX(), primary.cellY(), variation);
        Motion secondaryMotion = motionFor(secondary.cellX(), secondary.cellY(), variation);
        double relativeX = secondaryMotion.x() - primaryMotion.x();
        double relativeY = secondaryMotion.y() - primaryMotion.y();
        double normalRate = (relativeX * normalX + relativeY * normalY) * 0.5d;
        double tangentRate = (-relativeX * normalY + relativeY * normalX) * 0.5d;

        MacroGeophysicalBoundaryRegime regime;
        double boundaryStrength;
        if (boundaryInfluence < ACTIVE_BOUNDARY_INFLUENCE) {
            regime = MacroGeophysicalBoundaryRegime.INTERIOR;
            boundaryStrength = 0.0d;
        } else {
            double normalMagnitude = Math.abs(normalRate);
            double tangentMagnitude = Math.abs(tangentRate);
            if (normalMagnitude >= 0.12d && normalMagnitude >= tangentMagnitude * 0.75d) {
                regime = normalRate < 0.0d
                        ? MacroGeophysicalBoundaryRegime.CONVERGENT
                        : MacroGeophysicalBoundaryRegime.DIVERGENT;
                boundaryStrength = clamp(normalMagnitude, 0.0d, 1.0d);
            } else {
                regime = MacroGeophysicalBoundaryRegime.TRANSFORM;
                boundaryStrength = clamp(Math.max(normalMagnitude, tangentMagnitude), 0.0d, 1.0d);
            }
        }

        return new MacroGeophysicalStructure(
                continentalSupport,
                clamp(marginInfluence, 0.0d, 1.0d),
                primary.id(),
                secondary.id(),
                clamp(boundaryInfluence, 0.0d, 1.0d),
                regime,
                boundaryStrength,
                normalX,
                normalY);
    }

    private StructuralSitePair nearestStructuralSites(double x, double y) {
        long baseX = (long) Math.floor(x / structureSpan);
        long baseY = (long) Math.floor(y / structureSpan);
        StructuralSite primary = null;
        StructuralSite secondary = null;
        double primaryDistanceSquared = Double.POSITIVE_INFINITY;
        double secondaryDistanceSquared = Double.POSITIVE_INFINITY;

        // Jitter is below one third of a cell, so a 5x5 neighborhood is a conservative fixed bound
        // for the nearest and second-nearest sites at any query coordinate.
        for (int offsetY = -2; offsetY <= 2; offsetY++) {
            for (int offsetX = -2; offsetX <= 2; offsetX++) {
                StructuralSite candidate = structuralSite(baseX + offsetX, baseY + offsetY);
                double dx = x - candidate.centerX();
                double dy = y - candidate.centerY();
                double distanceSquared = dx * dx + dy * dy;
                if (distanceSquared < primaryDistanceSquared) {
                    secondary = primary;
                    secondaryDistanceSquared = primaryDistanceSquared;
                    primary = candidate;
                    primaryDistanceSquared = distanceSquared;
                } else if (distanceSquared < secondaryDistanceSquared) {
                    secondary = candidate;
                    secondaryDistanceSquared = distanceSquared;
                }
            }
        }

        if (primary == null || secondary == null) {
            throw new IllegalStateException("fixed structural neighborhood did not produce two regions");
        }
        return new StructuralSitePair(
                primary,
                secondary,
                primaryDistanceSquared,
                secondaryDistanceSquared);
    }

    private StructuralSite structuralSite(long cellX, long cellY) {
        double centerX = (cellX + 0.5d) * structureSpan
                + signedUnit(structuralHash(cellX, cellY, STRUCTURE_JITTER_X_SALT))
                        * structureSpan
                        * STRUCTURE_JITTER;
        double centerY = (cellY + 0.5d) * structureSpan
                + signedUnit(structuralHash(cellX, cellY, STRUCTURE_JITTER_Y_SALT))
                        * structureSpan
                        * STRUCTURE_JITTER;
        long idValue = structuralHash(cellX, cellY, STRUCTURE_REGION_ID_SALT);
        return new StructuralSite(
                cellX,
                cellY,
                centerX,
                centerY,
                new MacroGeophysicalRegionId(idValue));
    }

    private Motion motionFor(long cellX, long cellY, double variation) {
        double angle = unitDouble(structuralHash(cellX, cellY, STRUCTURE_MOTION_ANGLE_SALT))
                * Math.PI
                * 2.0d;
        double authoredScale = lerp(0.45d, 1.0d, variation);
        double speed = (0.35d
                        + unitDouble(structuralHash(cellX, cellY, STRUCTURE_MOTION_SPEED_SALT))
                                * 0.65d)
                * authoredScale;
        return new Motion(Math.cos(angle) * speed, Math.sin(angle) * speed);
    }

    private double continentalSupportAt(double x, double y) {
        double primary = gradientNoiseAt(x, y, continentSpan, CONTINENT_SALT);
        double secondary = gradientNoiseAt(
                x,
                y,
                Math.max(MIN_PROVINCE_SPAN, continentSpan / 2.0d),
                CONTINENT_SECONDARY_SALT);
        double tertiary = gradientNoiseAt(
                x,
                y,
                Math.max(MIN_PROVINCE_SPAN, continentSpan / 4.0d),
                CONTINENT_TERTIARY_SALT);
        return (primary + secondary * 0.48d + tertiary * 0.20d) / 1.68d;
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
        long value = mix64(seed ^ salt);
        value = mix64(value ^ mix64(cellX));
        value = mix64(value ^ Long.rotateLeft(mix64(cellY), 29));
        return mix64(value ^ revision);
    }

    private long structuralHash(long cellX, long cellY, long salt) {
        return latticeHash(cellX, cellY, salt);
    }

    private static double unitDouble(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static double signedUnit(long hash) {
        return unitDouble(hash) * 2.0d - 1.0d;
    }

    private static long continentSpan(double scale) {
        double ratio = MAX_CONTINENT_SPAN / (double) MIN_CONTINENT_SPAN;
        return Math.round(MIN_CONTINENT_SPAN * Math.pow(ratio, scale));
    }

    private static double stabilize(double support, double cohesion) {
        if (support == 0d) return 0d;
        double exponent = lerp(1.0d, 0.58d, cohesion);
        return Math.copySign(Math.pow(Math.abs(support), exponent), support);
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

    private record StructuralSite(
            long cellX,
            long cellY,
            double centerX,
            double centerY,
            MacroGeophysicalRegionId id) {}

    private record StructuralSitePair(
            StructuralSite primary,
            StructuralSite secondary,
            double primaryDistanceSquared,
            double secondaryDistanceSquared) {}

    private record Motion(double x, double y) {}
}
