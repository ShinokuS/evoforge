package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.genesis.LegacyV15Random;
import io.github.evoforge.simulation.world.terrain.genesis.V13MountainCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V13MountainRecipe;
import io.github.evoforge.simulation.world.terrain.genesis.V15TerrainCoordinateFrame;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinate-addressable large-world execution of the accepted V13 mountain morphology.
 *
 * <p>The finite V13 oracle globally sorts candidate systems. That global sort cannot remain
 * size-independent on an arbitrarily large Continuum domain. This large-world execution keeps the
 * accepted candidate lattice, center jitter, orientation, width/height variation, plateau profile,
 * authored physical widths and deterministic RNG, while replacing only the global candidate-count
 * sort with a deterministic activation threshold calibrated to the same expected source density.
 * Requested windows inspect only lattice candidates whose bounded support can touch that window.</p>
 */
public final class V15NativeMountainPageSource implements ContinuumScalarPageSource {
    private static final String STAGE = "world:mountains";
    private static final String ACTIVE = "mountain:active";
    private static final String CENTER = "mountain:center";
    private static final String ORIENTATION = "mountain:orientation";
    private static final String WIDTH = "mountain:width";
    private static final String HEIGHT = "mountain:height";
    private static final String PLATEAU = "mountain:plateau";
    private static final int PPM = 1_000_000;
    private static final double TWO_PI = StrictMath.PI * 2.0;
    private static final double MEAN_VISIBLE_FOOTPRINT_FRACTION = 0.72;
    private static final int MAX_CENTER_LAND_CACHE = 32_768;

    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource base;
    private final V13MountainCalibration calibration;
    private final V13MountainRecipe recipe;
    private final V15TerrainCoordinateFrame frame;
    private final LegacyV15Random random;
    private final int activationPpm;
    private final double maximumCandidateSupport;
    private final Map<Long, Boolean> centerLand = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > MAX_CENTER_LAND_CACHE;
        }
    };

    public V15NativeMountainPageSource(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition terrainDefinition,
            V13MountainDefinition mountainDefinition,
            ContinuumScalarPageSource base,
            int maximumZCells) {
        if (domain == null || terrainDefinition == null || mountainDefinition == null || base == null) {
            throw new IllegalArgumentException("native V15 mountain inputs must not be null");
        }
        if (!domain.equals(base.domain())) {
            throw new IllegalArgumentException("native V15 mountain base must match its domain");
        }
        this.domain = domain;
        this.base = base;
        this.recipe = V13MountainRecipe.balanced();
        this.calibration = V13MountainCalibration.compile(
                domain,
                mountainDefinition,
                recipe,
                maximumZCells);
        this.frame = V15TerrainCoordinateFrame.centered(domain);
        this.random = new LegacyV15Random(seed);
        this.activationPpm = activationPpm(terrainDefinition);
        this.maximumCandidateSupport = Math.max(
                        calibration.typicalHalfWidthCells(),
                        calibration.typicalLongAxisCells())
                * (1.0 + recipe.widthVariationPpm() / (double) PPM)
                + 2.0;
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        ContinuumScalarPage basePage = base.materialize(window);
        List<MountainSystem> systems = systemsFor(window);
        if (systems.isEmpty()) return basePage;

        double[] output = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            long y = window.yAt(sampleY);
            for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                long x = window.xAt(sampleX);
                long baseHeight = Math.round(basePage.sample(sampleX, sampleY));
                if (baseHeight <= 0L) {
                    output[cursor++] = baseHeight;
                    continue;
                }
                long rawUplift = rawUpliftAt(x, y, systems);
                if (rawUplift <= 0L) {
                    output[cursor++] = baseHeight;
                    continue;
                }
                long uplift = Math.round(rawUplift * inferredInlandFactor(baseHeight));
                output[cursor++] = Math.min(
                        calibration.mountainCeilingSubunits(),
                        Math.addExact(baseHeight, uplift));
            }
        }
        return new ContinuumScalarPage(window, output);
    }

    private List<MountainSystem> systemsFor(ContinuumSampleWindow window) {
        if (activationPpm <= 0
                || calibration.targetCoveragePpm() <= 0
                || calibration.typicalUpliftSubunits() <= 0L) {
            return List.of();
        }
        int spacing = calibration.candidateSpacingCells();
        double minLegacyX = frame.legacyX(window.minX()) - maximumCandidateSupport;
        double maxLegacyX = frame.legacyX(window.xAt(window.width() - 1)) + maximumCandidateSupport;
        double minLegacyY = frame.legacyY(window.minY()) - maximumCandidateSupport;
        double maxLegacyY = frame.legacyY(window.yAt(window.height() - 1)) + maximumCandidateSupport;
        long minLatticeX = (long) StrictMath.floor(minLegacyX / spacing) - 1L;
        long maxLatticeX = (long) StrictMath.floor(maxLegacyX / spacing) + 1L;
        long minLatticeY = (long) StrictMath.floor(minLegacyY / spacing) - 1L;
        long maxLatticeY = (long) StrictMath.floor(maxLegacyY / spacing) + 1L;

        List<MountainSystem> systems = new ArrayList<>();
        for (long latticeY = minLatticeY; latticeY <= maxLatticeY; latticeY++) {
            for (long latticeX = minLatticeX; latticeX <= maxLatticeX; latticeX++) {
                if (samplePpm(ACTIVE, latticeX, latticeY, 0L) >= activationPpm) continue;
                MountainSystem system = createSystem(latticeX, latticeY);
                if (system.upliftSubunits() <= 0L || !centerIsLand(system)) continue;
                systems.add(system);
            }
        }
        return systems;
    }

    private boolean centerIsLand(MountainSystem system) {
        long legacyX = Math.round(system.centerX());
        long legacyY = Math.round(system.centerY());
        long x = legacyX - frame.legacyMinX();
        long y = legacyY - frame.legacyMinY();
        if (!domain.contains(x, y)) return false;
        long key = Math.addExact(Math.multiplyExact(y, domain.width()), x);
        synchronized (centerLand) {
            Boolean cached = centerLand.get(key);
            if (cached != null) return cached;
        }
        ContinuumScalarPage point = base.materialize(new ContinuumSampleWindow(x, y, 1, 1, 1));
        boolean land = point.sample(0, 0) > 0d;
        synchronized (centerLand) {
            centerLand.put(key, land);
        }
        return land;
    }

    private long rawUpliftAt(long x, long y, List<MountainSystem> systems) {
        double legacyX = frame.legacyX(x);
        double legacyY = frame.legacyY(y);
        long maximum = 0L;
        for (MountainSystem system : systems) {
            double profile = elongatedHillProfile(system, legacyX, legacyY);
            if (profile <= 0.0) continue;
            long uplift = Math.max(0L, Math.round(system.upliftSubunits() * profile));
            maximum = Math.max(maximum, uplift);
        }
        return maximum;
    }

    private MountainSystem createSystem(long latticeX, long latticeY) {
        int spacing = calibration.candidateSpacingCells();
        double centerX = latticeX * (double) spacing;
        double centerY = latticeY * (double) spacing;
        double maximumJitter = spacing * recipe.centerJitterPpm() / (double) PPM;
        centerX += centeredPpm(CENTER, latticeX, latticeY, 0L) * maximumJitter / PPM;
        centerY += centeredPpm(CENTER, latticeX, latticeY, 1L) * maximumJitter / PPM;

        double angle = samplePpm(ORIENTATION, latticeX, latticeY, 0L) * TWO_PI / PPM;
        double axisX = StrictMath.cos(angle);
        double axisY = StrictMath.sin(angle);

        double widthVariation = recipe.widthVariationPpm() / (double) PPM;
        double baseWidth = calibration.typicalHalfWidthCells();
        double leftWidth = variedPositive(
                baseWidth,
                centeredPpm(WIDTH, latticeX, latticeY, 0L),
                widthVariation);
        double rightWidth = variedPositive(
                baseWidth,
                centeredPpm(WIDTH, latticeX, latticeY, 1L),
                widthVariation);
        double longVariation = widthVariation * 0.72;
        double negativeLongAxis = variedPositive(
                calibration.typicalLongAxisCells(),
                centeredPpm(WIDTH, latticeX, latticeY, 2L),
                longVariation);
        double positiveLongAxis = variedPositive(
                calibration.typicalLongAxisCells(),
                centeredPpm(WIDTH, latticeX, latticeY, 3L),
                longVariation);
        double minimumLong = Math.max(leftWidth, rightWidth) * 0.92;
        negativeLongAxis = Math.max(minimumLong, negativeLongAxis);
        positiveLongAxis = Math.max(minimumLong, positiveLongAxis);

        boolean plateau = calibration.plateausEnabled()
                && samplePpm(PLATEAU, latticeX, latticeY, 0L)
                        < calibration.plateauProbabilityPpm();
        double heightVariation = recipe.heightVariationPpm() / (double) PPM;
        double upliftScale = 1.0
                + centeredPpm(HEIGHT, latticeX, latticeY, 0L) / (double) PPM
                        * heightVariation;
        long requestedUplift = Math.max(
                0L,
                Math.round(calibration.typicalUpliftSubunits() * upliftScale));
        double narrowAxis = Math.max(
                1.0,
                Math.min(
                        Math.min(leftWidth, rightWidth),
                        Math.min(negativeLongAxis, positiveLongAxis)));
        double gradientBound = recipe.profileGradientBound(plateau);
        long supportedUplift = Math.max(
                0L,
                (long) StrictMath.floor(
                        narrowAxis * calibration.maximumCardinalRiseSubunits() / gradientBound));
        long uplift = Math.min(requestedUplift, supportedUplift);
        return new MountainSystem(
                centerX,
                centerY,
                axisX,
                axisY,
                negativeLongAxis,
                positiveLongAxis,
                leftWidth,
                rightWidth,
                uplift,
                plateau);
    }

    private int activationPpm(V15TerrainDefinition definition) {
        if (calibration.targetCoveragePpm() <= 0 || calibration.typicalUpliftSubunits() <= 0L) return 0;
        double landFraction = definition.landCoverage().partsPerMillion() / (double) PPM;
        if (!(landFraction > 0d)) return 0;
        double landCells = domain.width() * (double) domain.height() * landFraction;
        double targetCells = landCells * calibration.targetCoveragePpm() / PPM;
        double nominalFootprint = StrictMath.PI
                * calibration.typicalHalfWidthCells()
                * (double) calibration.typicalLongAxisCells()
                * MEAN_VISIBLE_FOOTPRINT_FRACTION;
        if (!(targetCells > 0d) || !(nominalFootprint > 0d)) return 0;
        double desiredSources = StrictMath.ceil(targetCells / nominalFootprint);

        int spacing = calibration.candidateSpacingCells();
        long minLegacyX = frame.legacyMinX();
        long minLegacyY = frame.legacyMinY();
        long maxLegacyX = Math.addExact(minLegacyX, domain.width() - 1L);
        long maxLegacyY = Math.addExact(minLegacyY, domain.height() - 1L);
        long latticeWidth = Math.floorDiv(maxLegacyX, spacing) - Math.floorDiv(minLegacyX, spacing) + 3L;
        long latticeHeight = Math.floorDiv(maxLegacyY, spacing) - Math.floorDiv(minLegacyY, spacing) + 3L;
        double expectedLandCandidates = latticeWidth * (double) latticeHeight * landFraction;
        if (!(expectedLandCandidates > 0d)) return 0;
        return (int) Math.max(
                0L,
                Math.min((long) PPM, Math.round(desiredSources / expectedLandCandidates * PPM)));
    }

    private double inferredInlandFactor(long baseHeight) {
        long baseCeiling = Math.max(1L, calibration.baseTerrainCeilingSubunits());
        double coordinate = baseHeight / (double) baseCeiling;
        return Math.max(0.18d, Math.min(1d, coordinate / 0.30d));
    }

    private double elongatedHillProfile(MountainSystem system, double x, double y) {
        double dx = x - system.centerX();
        double dy = y - system.centerY();
        double along = dx * system.axisX() + dy * system.axisY();
        double across = -dx * system.axisY() + dy * system.axisX();
        double longAxis = along < 0.0 ? system.negativeLongAxis() : system.positiveLongAxis();
        double sideWidth = across < 0.0 ? system.leftWidth() : system.rightWidth();
        if (longAxis <= 0.0 || sideWidth <= 0.0) return 0.0;
        double radius = StrictMath.hypot(along / longAxis, across / sideWidth);
        if (radius >= 1.0) return 0.0;
        double sharpness = calibration.sharpnessMilli() / 1_000.0;
        return layeredHill(radius, sharpness, system.plateau());
    }

    private double layeredHill(double radius, double sharpness, boolean plateau) {
        double r = Math.max(0.0, Math.min(1.0, radius));
        if (plateau) {
            double plateauCore = recipe.plateauCorePpm() / (double) PPM;
            if (r <= plateauCore) return 1.0;
            r = (r - plateauCore) / (1.0 - plateauCore);
        }
        double character = Math.max(0.0, Math.min(1.0, (sharpness - 0.85) / 0.40));
        double summitEase = 0.22 - character * 0.12;
        double footEase = 0.14;
        double slope = 1.0 / (1.0 - (summitEase + footEase) * 0.5);
        if (r < summitEase) {
            return Math.max(0.0, 1.0 - slope * r * r / (2.0 * summitEase));
        }
        if (r <= 1.0 - footEase) {
            double summitValue = 1.0 - slope * summitEase * 0.5;
            return Math.max(0.0, summitValue - slope * (r - summitEase));
        }
        double remaining = 1.0 - r;
        return Math.max(0.0, slope * remaining * remaining / (2.0 * footEase));
    }

    private int samplePpm(String purpose, long x, long y, long ordinal) {
        long unsignedHigh = random.sample(STAGE, purpose, x, y, 0L, ordinal) >>> 32;
        return (int) (unsignedHigh * PPM / 0x1_0000_0000L);
    }

    private int centeredPpm(String purpose, long x, long y, long ordinal) {
        return samplePpm(purpose, x, y, ordinal) * 2 - PPM;
    }

    private static double variedPositive(double base, int centeredCoordinatePpm, double variation) {
        return Math.max(1.0, base * (1.0 + centeredCoordinatePpm / (double) PPM * variation));
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maximumX = window.xAt(window.width() - 1);
        long maximumY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maximumX, maximumY)) {
            throw new IllegalArgumentException("window lies outside native V15 mountain domain");
        }
    }

    private record MountainSystem(
            double centerX,
            double centerY,
            double axisX,
            double axisY,
            double negativeLongAxis,
            double positiveLongAxis,
            double leftWidth,
            double rightWidth,
            long upliftSubunits,
            boolean plateau) {}
}
