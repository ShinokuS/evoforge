package io.github.evoforge.simulation.world.geophysics;

/**
 * Bounded deterministic Stage 5 macro-geophysical model hidden behind {@link MacroGeophysics}.
 *
 * <p>The implementation evaluates two nested macro scales of one crustal-support process. The
 * broad scale establishes continent/ocean-basin sized support while the regional scale bends that
 * support into coherent provinces and island groups. Authored semantic controls are translated
 * here into internal spans and weights; those solver details are deliberately not part of the
 * public definition contract.</p>
 */
final class DeterministicMacroGeophysicalField implements MacroGeophysicalField {
    private static final long MIN_CONTINENT_SPAN = 1L << 20;
    private static final long MAX_CONTINENT_SPAN = 1L << 23;
    private static final long MIN_PROVINCE_SPAN = 1L << 18;

    private static final long CONTINENT_SALT = 0x5A17B4C39D2E61F0L;
    private static final long PROVINCE_SALT = 0xC3D2E1F05A174B69L;

    private final long seed;
    private final long revision;
    private final MacroGeophysicsDefinition definition;
    private final long continentSpan;
    private final long provinceSpan;

    DeterministicMacroGeophysicalField(
            long seed,
            long revision,
            MacroGeophysicsDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        this.seed = seed;
        this.revision = revision;
        this.definition = definition;
        this.continentSpan = continentSpan(definition.continentalScale().value());

        // Fragmentation is allowed to shorten the regional structural scale, but only inside a
        // macro-geographical band. It must never collapse into sample-scale coastline noise.
        double provinceDivisor = lerp(2.2d, 3.8d, definition.fragmentation().value());
        this.provinceSpan = Math.max(
                MIN_PROVINCE_SPAN,
                Math.round(continentSpan / provinceDivisor));
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

        double continentalSupport = supportAt(x, y, continentSpan, CONTINENT_SALT);
        double stableContinentalSupport = stabilize(continentalSupport, cohesion);
        double provinceSupport = supportAt(x, y, provinceSpan, PROVINCE_SALT);

        // Fragmentation lets coherent regional provinces interrupt broad support. The bounded
        // regional span above prevents high fragmentation from degenerating into tiny speckles.
        double regionalWeight = lerp(0.08d, 0.34d, fragmentation);
        double support = lerp(stableContinentalSupport, provinceSupport, regionalWeight);

        // Variation changes bounded regional deformation without introducing a separate painter.
        // Signed disagreement keeps the deformation causally coupled to the two support scales.
        double disagreement = provinceSupport - stableContinentalSupport;
        double deformationWeight = lerp(0.03d, 0.13d, variation);
        double deformation = disagreement * Math.abs(disagreement) * deformationWeight;

        // Ocean prevalence is an authored tendency rather than a promise of an exact global area
        // percentage. It shifts the shared elevation field relative to the fixed sea datum at zero.
        double seaBias = (0.5d - definition.oceanPrevalence().value()) * 0.85d;
        return clamp(support + deformation + seaBias, -1.0d, 1.0d);
    }

    private double supportAt(long x, long y, long span, long salt) {
        long cellX = Math.floorDiv(x, span);
        long cellY = Math.floorDiv(y, span);
        double localX = Math.floorMod(x, span) / (double) span;
        double localY = Math.floorMod(y, span) / (double) span;
        double blendX = smooth(localX);
        double blendY = smooth(localY);

        double v00 = latticeValue(cellX, cellY, salt);
        double v10 = latticeValue(cellX + 1L, cellY, salt);
        double v01 = latticeValue(cellX, cellY + 1L, salt);
        double v11 = latticeValue(cellX + 1L, cellY + 1L, salt);

        double lower = lerp(v00, v10, blendX);
        double upper = lerp(v01, v11, blendX);
        return lerp(lower, upper, blendY);
    }

    private double latticeValue(long cellX, long cellY, long salt) {
        long value = mix64(seed ^ salt);
        value = mix64(value ^ mix64(cellX));
        value = mix64(value ^ Long.rotateLeft(mix64(cellY), 29));
        value = mix64(value ^ revision);
        return ((value >>> 11) * 0x1.0p-53) * 2.0d - 1.0d;
    }

    private static long continentSpan(double scale) {
        double ratio = MAX_CONTINENT_SPAN / (double) MIN_CONTINENT_SPAN;
        return Math.round(MIN_CONTINENT_SPAN * Math.pow(ratio, scale));
    }

    private static double stabilize(double support, double cohesion) {
        if (support == 0d) return 0d;
        double exponent = lerp(1.0d, 0.5d, cohesion);
        return Math.copySign(Math.pow(Math.abs(support), exponent), support);
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
