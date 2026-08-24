package io.github.evoforge.simulation.world.geophysics;

/**
 * Bounded deterministic Stage 5 macro-geophysical model.
 *
 * <p>The implementation evaluates two nested scales of one crustal-support process. The broad scale
 * establishes continent/ocean-basin sized support while the regional scale perturbs that same
 * support into large geophysical provinces. Their disagreement contributes bounded deformation to
 * the skeleton. No terrain detail, erosion, drainage or exact XYZ materialization is performed.
 */
public final class DeterministicMacroGeophysicalField implements MacroGeophysicalField {
    private static final long CONTINENT_SPAN = 1L << 21;
    private static final long PROVINCE_SPAN = 1L << 18;

    private static final long CONTINENT_SALT = 0x5A17B4C39D2E61F0L;
    private static final long PROVINCE_SALT = 0xC3D2E1F05A174B69L;

    private final long seed;
    private final long revision;

    public DeterministicMacroGeophysicalField(long seed, long revision) {
        this.seed = seed;
        this.revision = revision;
    }

    public long seed() {
        return seed;
    }

    public long revision() {
        return revision;
    }

    @Override
    public double elevationAt(long x, long y) {
        double continentalSupport = supportAt(x, y, CONTINENT_SPAN, CONTINENT_SALT);
        double provinceSupport = supportAt(x, y, PROVINCE_SPAN, PROVINCE_SALT);

        // A regional province that disagrees with its broad continental support represents bounded
        // deformation of the same crustal-support system rather than an independent feature layer.
        double deformation = Math.abs(provinceSupport - continentalSupport);
        double elevation =
                continentalSupport * 0.74d
                        + provinceSupport * 0.26d
                        + (deformation - 0.35d) * 0.12d
                        - 0.06d;
        return clamp(elevation, -1.0d, 1.0d);
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
