package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.surface.SurfaceMorphologyField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * First causal spatial Soil-formation model.
 *
 * <p>The authored material profile remains an immutable archetype. Local convex exposure,
 * concavity and drainage accumulation continuously shift only the mineral-fineness coordinate
 * before physical composition and hydraulic calibration. There are no texture classes, coordinate
 * hashes or material-name switches. Organic character is intentionally preserved until vegetation,
 * climate history and pedogenesis provide causal inputs for changing it.</p>
 */
public final class SoilFormationGenerationStage implements SoilFormationGenerator {
    private static final BigInteger NORMALIZED_SCALE =
            BigInteger.valueOf(NormalizedValue.SCALE);

    private final SoilFormationCalibration formationCalibration;
    private final SoilCompositionCompiler compositionCompiler;
    private final SoilHydraulicCalibrator hydraulicCalibrator;

    public SoilFormationGenerationStage(
            SoilFormationCalibration formationCalibration,
            SoilCompositionCompiler compositionCompiler,
            SoilHydraulicCalibrator hydraulicCalibrator) {
        if (formationCalibration == null
                || compositionCompiler == null
                || hydraulicCalibrator == null) {
            throw new IllegalArgumentException("soil formation dependencies must not be null");
        }
        this.formationCalibration = formationCalibration;
        this.compositionCompiler = compositionCompiler;
        this.hydraulicCalibrator = hydraulicCalibrator;
    }

    public static SoilFormationGenerationStage standard() {
        return new SoilFormationGenerationStage(
                SoilFormationCalibration.representative(),
                new ContinuousSoilCompositionCompiler(SoilCompositionCalibration.representative()),
                new SaxtonRawls2006SoilHydraulicCalibrator());
    }

    @Override
    public SoilHydraulicProfileField generate(
            TerrainMaterialField materials,
            SurfaceMorphologyField morphology,
            DrainageField drainage,
            SoilSemanticProfileBindings semantics) {
        if (materials == null || morphology == null || drainage == null || semantics == null) {
            throw new IllegalArgumentException("soil formation inputs must not be null");
        }
        WorldBounds bounds = materials.bounds();
        if (!bounds.equals(morphology.bounds()) || !bounds.equals(drainage.bounds())) {
            throw new IllegalArgumentException(
                    "soil formation material, morphology and drainage bounds must match");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));
        Map<TerrainMaterialKey, SoilHydraulicProfile[]> profiles = new LinkedHashMap<>();

        for (Map.Entry<TerrainMaterialKey, SoilSemanticProfile> entry
                : semantics.asMap().entrySet()) {
            SoilHydraulicProfile[] byColumn = new SoilHydraulicProfile[area];
            int index = 0;
            for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
                int worldY = (int) y;
                for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                    int worldX = (int) x;
                    SoilSemanticProfile developed = develop(
                            entry.getValue(),
                            morphology,
                            drainage,
                            area,
                            worldX,
                            worldY);
                    SoilCompositionProfile composition = compositionCompiler.compile(developed);
                    if (composition == null) {
                        throw new IllegalStateException("soil composition compiler returned null");
                    }
                    SoilHydraulicProfile hydraulics = hydraulicCalibrator.calibrate(composition);
                    if (hydraulics == null) {
                        throw new IllegalStateException("soil hydraulic calibrator returned null");
                    }
                    byColumn[index++] = hydraulics;
                }
            }
            profiles.put(entry.getKey(), byColumn);
        }

        return new DevelopedSoilHydraulicProfileField(bounds, width, materials, profiles);
    }

    private SoilSemanticProfile develop(
            SoilSemanticProfile base,
            SurfaceMorphologyField morphology,
            DrainageField drainage,
            long horizontalArea,
            int x,
            int y) {
        long convexity = requireNonNegative(
                morphology.convexitySubunitsAt(x, y),
                "surface convexity");
        long concavity = requireNonNegative(
                morphology.concavitySubunitsAt(x, y),
                "surface concavity");

        int exposureResponse = smoothResponse(
                convexity,
                formationCalibration.convexityCharacteristicSubunits());
        int concavityResponse = smoothResponse(
                concavity,
                formationCalibration.concavityCharacteristicSubunits());
        int drainageResponse = drainageResponse(
                drainage.contributingAreaAt(x, y),
                horizontalArea);

        int accumulationResponse = Math.toIntExact(roundDivide(
                (long) concavityResponse * (NormalizedValue.SCALE + (long) drainageResponse),
                2L * NormalizedValue.SCALE));
        int netGeomorphicResponse = accumulationResponse - exposureResponse;
        int maximumShift = formationCalibration.maximumMineralFinenessShift().partsPerMillion();
        int finenessShift = Math.toIntExact(roundDivideSigned(
                (long) netGeomorphicResponse * maximumShift,
                NormalizedValue.SCALE));
        int developedFineness = clampNormalized(
                (long) base.mineralFineness().partsPerMillion() + finenessShift);

        return new SoilSemanticProfile(
                NormalizedValue.ofPartsPerMillion(developedFineness),
                base.organicMatter());
    }

    private static int smoothResponse(long value, long characteristicScale) {
        if (value == 0L) return 0;
        BigInteger numerator = BigInteger.valueOf(value).multiply(NORMALIZED_SCALE);
        BigInteger denominator = BigInteger.valueOf(value)
                .add(BigInteger.valueOf(characteristicScale));
        return numerator.divide(denominator).intValueExact();
    }

    private static int drainageResponse(long contributingArea, long horizontalArea) {
        if (contributingArea < 1L || contributingArea > horizontalArea) {
            throw new IllegalStateException(
                    "drainage contributing area must be within 1..world horizontal area: "
                            + contributingArea);
        }
        if (horizontalArea == 1L) return 0;
        BigInteger numerator = BigInteger.valueOf(contributingArea - 1L)
                .multiply(NORMALIZED_SCALE);
        BigInteger denominator = BigInteger.valueOf(horizontalArea - 1L);
        return numerator.divide(denominator).intValueExact();
    }

    private static long requireNonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalStateException(label + " must be non-negative: " + value);
        }
        return value;
    }

    private static int clampNormalized(long value) {
        if (value <= 0L) return 0;
        if (value >= NormalizedValue.SCALE) return NormalizedValue.SCALE;
        return Math.toIntExact(value);
    }

    private static long roundDivide(long numerator, long denominator) {
        return Math.addExact(numerator, denominator / 2L) / denominator;
    }

    private static long roundDivideSigned(long numerator, long denominator) {
        if (numerator >= 0L) return roundDivide(numerator, denominator);
        return -roundDivide(-numerator, denominator);
    }

    private static final class DevelopedSoilHydraulicProfileField
            implements SoilHydraulicProfileField {
        private final WorldBounds bounds;
        private final int width;
        private final TerrainMaterialField materials;
        private final Map<TerrainMaterialKey, SoilHydraulicProfile[]> profiles;

        private DevelopedSoilHydraulicProfileField(
                WorldBounds bounds,
                int width,
                TerrainMaterialField materials,
                Map<TerrainMaterialKey, SoilHydraulicProfile[]> profiles) {
            this.bounds = bounds;
            this.width = width;
            this.materials = materials;
            Map<TerrainMaterialKey, SoilHydraulicProfile[]> immutable = new LinkedHashMap<>();
            for (Map.Entry<TerrainMaterialKey, SoilHydraulicProfile[]> entry : profiles.entrySet()) {
                immutable.put(entry.getKey(), entry.getValue().clone());
            }
            this.profiles = Map.copyOf(immutable);
        }

        @Override
        public WorldBounds bounds() {
            return bounds;
        }

        @Override
        public SoilHydraulicProfile find(int x, int y, int z) {
            TerrainMaterialKey material = materials.materialAt(x, y, z);
            SoilHydraulicProfile[] byColumn = profiles.get(material);
            if (byColumn == null) return null;
            return byColumn[indexOf(x, y)];
        }

        private int indexOf(int x, int y) {
            if (x < bounds.minX() || x > bounds.maxX()
                    || y < bounds.minY() || y > bounds.maxY()) {
                throw new IllegalArgumentException(
                        "soil formation coordinate outside world bounds: (" + x + ", " + y + ")");
            }
            return (y - bounds.minY()) * width + (x - bounds.minX());
        }
    }
}
