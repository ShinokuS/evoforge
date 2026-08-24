package io.github.evoforge.simulation.world.geophysics;

/**
 * Bounded deterministic Stage 5 macro-geophysical model hidden behind {@link MacroGeophysics}.
 *
 * <p>The field is intentionally low-frequency. A domain-warped gradient field establishes broad
 * continental support, two progressively smaller (but still macro-scale) octaves break symmetry,
 * and regional structure is allowed to reshape only the coastal transition band. High
 * fragmentation can additionally lift narrow regional boundary ridges into island chains. This
 * keeps continental interiors coherent and prevents authored fragmentation from degenerating into
 * sample-scale speckle.</p>
 */
final class DeterministicMacroGeophysicalField implements MacroGeophysicalField {
    private static final long MIN_CONTINENT_SPAN = 1L << 20;
    private static final long MAX_CONTINENT_SPAN = 1L << 23;
    private static final long MIN_PROVINCE_SPAN = 1L << 18;
    private static final double INV_SQRT_2 = 0.7071067811865476d;

    private static final long CONTINENT_SALT = 0x5A17B4C39D2E61F0L;
    private static final long CONTINENT_SECONDARY_SALT = 0x19C6D87A42E5B301L;
    private static final long CONTINENT_TERTIARY_SALT = 0x8B31E6C5A70D249FL;
    private static final long PROVINCE_SALT = 0xC3D2E1F05A174B69L;
    private static final long ISLAND_ARC_SALT = 0x7419A0E5D236BC8FL;
    private static final long WARP_X_SALT = 0x29D1C7B4E8530A6FL;
    private static final long WARP_Y_SALT = 0xE4B68A172D95C30FL;

    private final long seed;
    private final long revision;
    private final MacroGeophysicsDefinition definition;
    private final long continentSpan;
    private final double provinceSpan;

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
}
