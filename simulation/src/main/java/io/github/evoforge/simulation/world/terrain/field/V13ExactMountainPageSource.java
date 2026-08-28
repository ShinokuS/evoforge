package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.LegacyV15Random;
import io.github.evoforge.simulation.world.terrain.genesis.V12LandRankPlan;
import io.github.evoforge.simulation.world.terrain.genesis.V13MountainCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V13MountainRecipe;
import io.github.evoforge.simulation.world.terrain.genesis.V15TerrainCoordinateFrame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Exact Continuum execution of the accepted historical V13 mountain morphology. */
public final class V13ExactMountainPageSource implements ContinuumScalarPageSource {
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

    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource base;
    private final V12LandRankPlan land;
    private final V13MountainCalibration calibration;
    private final V13MountainRecipe recipe;
    private final V15TerrainCoordinateFrame frame;
    private final LegacyV15Random random;
    private final List<MountainSystem> systems;
    private final long maximumRawUplift;
    private final int[] boundedCoastalDistance;

    public V13ExactMountainPageSource(
            ContinuumWorldDomain domain,
            long seed,
            ContinuumScalarPageSource base,
            V12LandRankPlan land,
            V13MountainCalibration calibration,
            V13MountainRecipe recipe) {
        if (domain == null || base == null || land == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("V13 mountain inputs must not be null");
        }
        if (!domain.equals(base.domain())) {
            throw new IllegalArgumentException("V13 base page source must match the mountain domain");
        }
        this.domain = domain;
        this.base = base;
        this.land = land;
        this.calibration = calibration;
        this.recipe = recipe;
        this.frame = V15TerrainCoordinateFrame.centered(domain);
        this.random = new LegacyV15Random(seed);
        this.systems = List.copyOf(createSystems());
        this.maximumRawUplift = findMaximumRawUplift();
        this.boundedCoastalDistance = captureBoundedCoastalDistance();
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        ContinuumScalarPage basePage = base.materialize(window);
        double[] result = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            long y = window.yAt(sampleY);
            for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                long x = window.xAt(sampleX);
                long baseHeight = Math.round(basePage.sample(sampleX, sampleY));
                long uplift = finalUpliftAt(x, y);
                result[cursor++] = uplift <= 0L
                        ? baseHeight
                        : Math.min(
                                calibration.mountainCeilingSubunits(),
                                Math.addExact(baseHeight, uplift));
            }
        }
        return new ContinuumScalarPage(window, result);
    }

    private List<MountainSystem> createSystems() {
        if (calibration.targetCoveragePpm() == 0
                || calibration.typicalUpliftSubunits() == 0L
                || land.landCount() == 0L) {
            return List.of();
        }

        int spacing = calibration.candidateSpacingCells();
        long minLegacyX = frame.legacyMinX();
        long minLegacyY = frame.legacyMinY();
        long maxLegacyX = Math.addExact(minLegacyX, domain.width() - 1L);
        long maxLegacyY = Math.addExact(minLegacyY, domain.height() - 1L);
        long minimumLatticeX = Math.floorDiv(minLegacyX, spacing) - 1L;
        long maximumLatticeX = Math.floorDiv(maxLegacyX, spacing) + 1L;
        long minimumLatticeY = Math.floorDiv(minLegacyY, spacing) - 1L;
        long maximumLatticeY = Math.floorDiv(maxLegacyY, spacing) + 1L;

        List<MountainCandidate> candidates = new ArrayList<>();
        for (long latticeY = minimumLatticeY; latticeY <= maximumLatticeY; latticeY++) {
            for (long latticeX = minimumLatticeX; latticeX <= maximumLatticeX; latticeX++) {
                MountainSystem system = createSystem(latticeX, latticeY);
                if (!centerIsLand(system)) continue;
                int priority = samplePpm(ACTIVE, latticeX, latticeY, 0L);
                candidates.add(new MountainCandidate(priority, latticeX, latticeY, system));
            }
        }
        if (candidates.isEmpty()) return List.of();

        candidates.sort(Comparator
                .comparingInt(MountainCandidate::priority)
                .thenComparingLong(MountainCandidate::latticeY)
                .thenComparingLong(MountainCandidate::latticeX));

        int desiredSources = desiredSourceCount();
        List<MountainSystem> selected = new ArrayList<>(Math.min(desiredSources, candidates.size()));
        double minimumCenterDistance = calibration.typicalHalfWidthCells() * 1.20;
        double minimumCenterDistanceSquared = minimumCenterDistance * minimumCenterDistance;
        for (MountainCandidate candidate : candidates) {
            if (selected.size() >= desiredSources) break;
            if (tooCloseToSelected(candidate.system(), selected, minimumCenterDistanceSquared)) continue;
            selected.add(candidate.system());
        }

        if (selected.size() < desiredSources) {
            for (MountainCandidate candidate : candidates) {
                if (selected.size() >= desiredSources) break;
                if (selected.contains(candidate.system())) continue;
                selected.add(candidate.system());
            }
        }
        return selected;
    }

    private int desiredSourceCount() {
        double targetCells = (double) land.landCount() * calibration.targetCoveragePpm() / PPM;
        double nominalFootprint = StrictMath.PI
                * calibration.typicalHalfWidthCells()
                * (double) calibration.typicalLongAxisCells()
                * MEAN_VISIBLE_FOOTPRINT_FRACTION;
        if (targetCells <= 0.0 || nominalFootprint <= 0.0) return 0;
        return Math.max(1, (int) StrictMath.ceil(targetCells / nominalFootprint));
    }

    private boolean centerIsLand(MountainSystem system) {
        long legacyX = Math.round(system.centerX());
        long legacyY = Math.round(system.centerY());
        long x = legacyX - frame.legacyMinX();
        long y = legacyY - frame.legacyMinY();
        return domain.contains(x, y) && land.isLand(x, y);
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

    /**
     * Finds the same global maximum as the historical dense rasterizer without evaluating every
     * mountain system at every world cell. Each system can contribute only inside its finite
     * support box, so the maximum of the composed max-field is the maximum contribution encountered
     * while visiting those boxes.
     */
    private long findMaximumRawUplift() {
        if (systems.isEmpty()) return 0L;
        long minimumLegacyX = frame.legacyMinX();
        long minimumLegacyY = frame.legacyMinY();
        long maximumLegacyX = Math.addExact(minimumLegacyX, domain.width() - 1L);
        long maximumLegacyY = Math.addExact(minimumLegacyY, domain.height() - 1L);
        long maximum = 0L;
        for (MountainSystem system : systems) {
            double support = Math.max(
                    Math.max(system.negativeLongAxis(), system.positiveLongAxis()),
                    Math.max(system.leftWidth(), system.rightWidth()));
            long minX = Math.max(minimumLegacyX, (long) StrictMath.floor(system.centerX() - support));
            long maxX = Math.min(maximumLegacyX, (long) StrictMath.ceil(system.centerX() + support));
            long minY = Math.max(minimumLegacyY, (long) StrictMath.floor(system.centerY() - support));
            long maxY = Math.min(maximumLegacyY, (long) StrictMath.ceil(system.centerY() + support));
            if (minX > maxX || minY > maxY) continue;

            for (long legacyY = minY; legacyY <= maxY; legacyY++) {
                long y = legacyY - minimumLegacyY;
                for (long legacyX = minX; legacyX <= maxX; legacyX++) {
                    long x = legacyX - minimumLegacyX;
                    if (!land.isLand(x, y)) continue;
                    double profile = elongatedHillProfile(system, legacyX, legacyY);
                    if (profile <= 0.0) continue;
                    long uplift = Math.max(0L, Math.round(system.upliftSubunits() * profile));
                    if (uplift > maximum) maximum = uplift;
                }
            }
        }
        return maximum;
    }

    private long finalUpliftAt(long x, long y) {
        if (!land.isLand(x, y)) return 0L;
        long raw = rawUpliftAt(x, y);
        if (raw <= 0L || maximumRawUplift <= 0L) return raw;

        int transition = Math.max(1, calibration.coastalTransitionCells());
        long shoreline = Math.max(
                0L,
                Math.min(
                        maximumRawUplift,
                        Math.min(
                                calibration.shorelineUpliftSubunits(),
                                calibration.maximumCardinalRiseSubunits())));
        long inlandRise = maximumRawUplift - shoreline;
        long risePerCell = inlandRise == 0L
                ? 0L
                : (inlandRise + transition - 1L) / transition;
        risePerCell = Math.min(calibration.maximumCardinalRiseSubunits(), risePerCell);
        long inlandSteps = Math.max(0L, (long) coastalDistanceAt(x, y) - 1L);
        long allowedRise = Math.min(inlandRise, Math.multiplyExact(inlandSteps, risePerCell));
        long cap = Math.addExact(shoreline, allowedRise);
        return Math.min(raw, cap);
    }

    private long rawUpliftAt(long x, long y) {
        if (!land.isLand(x, y) || systems.isEmpty()) return 0L;
        double legacyX = frame.legacyX(x);
        double legacyY = frame.legacyY(y);
        long maximum = 0L;
        for (MountainSystem system : systems) {
            double profile = elongatedHillProfile(system, legacyX, legacyY);
            if (profile <= 0.0) continue;
            long uplift = Math.max(0L, Math.round(system.upliftSubunits() * profile));
            if (uplift > maximum) maximum = uplift;
        }
        return maximum;
    }

    private double elongatedHillProfile(MountainSystem system, double x, double y) {
        double dx = x - system.centerX();
        double dy = y - system.centerY();
        double along = dx * system.axisX() + dy * system.axisY();
        double across = -dx * system.axisY() + dy * system.axisX();
        double longAxis = along < 0.0 ? system.negativeLongAxis() : system.positiveLongAxis();
        double sideWidth = across < 0.0 ? system.leftWidth() : system.rightWidth();
        if (longAxis <= 0.0 || sideWidth <= 0.0) return 0.0;

        double normalizedAlong = along / longAxis;
        double normalizedAcross = across / sideWidth;
        double radius = StrictMath.hypot(normalizedAlong, normalizedAcross);
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

    private int coastalDistanceAt(long x, long y) {
        if (boundedCoastalDistance != null) {
            int width = Math.toIntExact(domain.width());
            return boundedCoastalDistance[Math.toIntExact(y) * width + Math.toIntExact(x)];
        }

        int cap = Math.max(1, calibration.coastalTransitionCells() + 1);
        long boundaryDistance = Math.min(
                Math.min(x + 1L, domain.width() - x),
                Math.min(y + 1L, domain.height() - y));
        int best = Math.toIntExact(Math.min((long) cap, boundaryDistance));
        for (int distance = 1; distance < best; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                int dy = distance - Math.abs(dx);
                long nx = x + dx;
                long firstY = y + dy;
                if (domain.contains(nx, firstY) && !land.isLand(nx, firstY)) return distance;
                if (dy != 0) {
                    long secondY = y - dy;
                    if (domain.contains(nx, secondY) && !land.isLand(nx, secondY)) return distance;
                }
            }
        }
        return best;
    }

    /**
     * Small finite parity/inspection worlds get the exact historical two-pass cardinal distance
     * field once. The cache has the same hard 512^2 ceiling as bounded exact terrain snapshots, so
     * it cannot grow with the Continuum address space; larger worlds retain bounded point queries.
     */
    private int[] captureBoundedCoastalDistance() {
        long widthLong = domain.width();
        long heightLong = domain.height();
        if (widthLong > BoundedExactTerrainSnapshotPageSource.MAX_SNAPSHOT_CELLS
                || heightLong > BoundedExactTerrainSnapshotPageSource.MAX_SNAPSHOT_CELLS) {
            return null;
        }
        long areaLong = Math.multiplyExact(widthLong, heightLong);
        if (areaLong > BoundedExactTerrainSnapshotPageSource.MAX_SNAPSHOT_CELLS) return null;

        int width = Math.toIntExact(widthLong);
        int height = Math.toIntExact(heightLong);
        int cap = Math.max(1, calibration.coastalTransitionCells() + 1);
        int[] distance = new int[Math.toIntExact(areaLong)];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (!land.isLand(x, y)) {
                    distance[cell] = 0;
                } else if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    distance[cell] = 1;
                } else {
                    distance[cell] = cap;
                }
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (!land.isLand(x, y)) continue;
                int best = distance[cell];
                if (x > 0) best = Math.min(best, distance[cell - 1] + 1);
                if (y > 0) best = Math.min(best, distance[cell - width] + 1);
                distance[cell] = Math.min(cap, best);
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int cell = y * width + x;
                if (!land.isLand(x, y)) continue;
                int best = distance[cell];
                if (x + 1 < width) best = Math.min(best, distance[cell + 1] + 1);
                if (y + 1 < height) best = Math.min(best, distance[cell + width] + 1);
                distance[cell] = Math.min(cap, best);
            }
        }
        return distance;
    }

    private int samplePpm(String purpose, long x, long y, long ordinal) {
        long unsignedHigh = random.sample(STAGE, purpose, x, y, 0L, ordinal) >>> 32;
        return (int) (unsignedHigh * PPM / 0x1_0000_0000L);
    }

    private int centeredPpm(String purpose, long x, long y, long ordinal) {
        return samplePpm(purpose, x, y, ordinal) * 2 - PPM;
    }

    private static boolean tooCloseToSelected(
            MountainSystem candidate,
            List<MountainSystem> selected,
            double minimumDistanceSquared) {
        for (MountainSystem existing : selected) {
            double dx = candidate.centerX() - existing.centerX();
            double dy = candidate.centerY() - existing.centerY();
            if (dx * dx + dy * dy < minimumDistanceSquared) return true;
        }
        return false;
    }

    private static double variedPositive(double base, int centeredCoordinatePpm, double variation) {
        return Math.max(1.0, base * (1.0 + centeredCoordinatePpm / (double) PPM * variation));
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside V13 terrain domain");
        }
    }

    private record MountainCandidate(
            int priority,
            long latticeX,
            long latticeY,
            MountainSystem system) {}

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
