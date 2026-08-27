package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V15InlandLakeDomainCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V15InlandLakeDomainRecipe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Exact prepared V15 inland-lake footprint.
 *
 * <p>The historical lowland selection is a global decision. Full-world primitive working state is
 * therefore reproduced only in disposable file-backed workspace grids during preparation. The
 * retained authoritative fact is the sparse row-major set of selected lake runs, not a world-sized
 * raster.</p>
 */
public final class V15InlandLakeDomainPlan {
    private static final int PPM = 1_000_000;
    private static final int DISTANCE_SCALE = 1_000;
    private static final int CARDINAL_DISTANCE = DISTANCE_SCALE;
    private static final int DIAGONAL_DISTANCE = 1_414;
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE / 4;
    private static final int BASE_STAGING_ROWS = 256;

    private final ContinuumWorldDomain domain;
    private final long[] runStarts;
    private final long[] runEnds;
    private final int lakeCellCount;
    private final V15InlandLakeDomainCalibration calibration;

    private V15InlandLakeDomainPlan(
            ContinuumWorldDomain domain,
            long[] runStarts,
            long[] runEnds,
            int lakeCellCount,
            V15InlandLakeDomainCalibration calibration) {
        this.domain = domain;
        this.runStarts = runStarts;
        this.runEnds = runEnds;
        this.lakeCellCount = lakeCellCount;
        this.calibration = calibration;
    }

    /** Production preparation using the exact historical scale-aware calibration. */
    public static V15InlandLakeDomainPlan prepare(
            ContinuumWorldDomain domain,
            ContinuumScalarPageSource continentalBase,
            int maximumZCells,
            V15InlandLakeDomainRecipe recipe) {
        validateInputs(domain, continentalBase, recipe);
        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        int area = Math.multiplyExact(width, height);
        try (TemporaryTerrainWorkspace workspace = new TemporaryTerrainWorkspace()) {
            TemporaryTerrainWorkspace.LongGrid elevation = workspace.longGrid(area);
            TemporaryTerrainWorkspace.ByteGrid dry = workspace.byteGrid(area);
            int dryLandCells = materializeBase(continentalBase, elevation, dry, width, height);
            V15InlandLakeDomainCalibration calibration = V15InlandLakeDomainCalibration.compile(
                    domain, dryLandCells, maximumZCells, recipe);
            return select(domain, workspace, elevation, dry, calibration, recipe);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to prepare exact V15 inland-lake domain", exception);
        }
    }

    /** Explicit-calibration seam used by direct historical-oracle parity fixtures. */
    public static V15InlandLakeDomainPlan prepare(
            ContinuumWorldDomain domain,
            ContinuumScalarPageSource continentalBase,
            V15InlandLakeDomainCalibration calibration,
            V15InlandLakeDomainRecipe recipe) {
        validateInputs(domain, continentalBase, recipe);
        if (calibration == null
                || calibration.width() != domain.width()
                || calibration.height() != domain.height()) {
            throw new IllegalArgumentException("V15 lake calibration must match its Continuum domain");
        }
        int width = calibration.width();
        int height = calibration.height();
        try (TemporaryTerrainWorkspace workspace = new TemporaryTerrainWorkspace()) {
            TemporaryTerrainWorkspace.LongGrid elevation = workspace.longGrid(calibration.area());
            TemporaryTerrainWorkspace.ByteGrid dry = workspace.byteGrid(calibration.area());
            int actualDry = materializeBase(continentalBase, elevation, dry, width, height);
            if (actualDry != calibration.dryLandCells()) {
                throw new IllegalArgumentException(
                        "explicit V15 lake calibration dry-land count does not match the base");
            }
            return select(domain, workspace, elevation, dry, calibration, recipe);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to prepare exact V15 inland-lake domain", exception);
        }
    }

    public ContinuumWorldDomain domain() {
        return domain;
    }

    public int lakeCellCount() {
        return lakeCellCount;
    }

    public V15InlandLakeDomainCalibration calibration() {
        return calibration;
    }

    public boolean isLake(long x, long y) {
        if (!domain.contains(x, y)) {
            throw new IllegalArgumentException("coordinate lies outside the V15 lake domain");
        }
        if (runStarts.length == 0) return false;
        long cell = Math.addExact(Math.multiplyExact(y, domain.width()), x);
        int low = 0;
        int high = runStarts.length - 1;
        int candidate = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (runStarts[middle] <= cell) {
                candidate = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return candidate >= 0 && cell <= runEnds[candidate];
    }

    /** Reproduces the historical post-compensation invariant before lake cells are materialized. */
    public void verifyDrySupport(ContinuumScalarPageSource authoritativeBase) {
        if (authoritativeBase == null || !domain.equals(authoritativeBase.domain())) {
            throw new IllegalArgumentException("authoritative V15 base must match the lake domain");
        }
        if (lakeCellCount == 0) return;
        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        for (int minY = 0; minY < height; minY += BASE_STAGING_ROWS) {
            int rows = Math.min(BASE_STAGING_ROWS, height - minY);
            ContinuumScalarPage page = authoritativeBase.materialize(
                    new ContinuumSampleWindow(0L, minY, width, rows, 1L));
            for (int localY = 0; localY < rows; localY++) {
                long y = minY + (long) localY;
                for (int x = 0; x < width; x++) {
                    if (!isLake(x, y)) continue;
                    if (Math.round(page.sample(x, localY)) <= 0L) {
                        throw new IllegalStateException(
                                "land-budget compensation failed to preserve lake-domain dry support at "
                                        + x + "," + y);
                    }
                }
            }
        }
    }

    private static V15InlandLakeDomainPlan select(
            ContinuumWorldDomain domain,
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.ByteGrid dry,
            V15InlandLakeDomainCalibration calibration,
            V15InlandLakeDomainRecipe recipe) throws IOException {
        int width = calibration.width();
        int height = calibration.height();
        int area = calibration.area();
        if (calibration.targetLakeCells() == 0 || calibration.dryLandCells() == 0) {
            return empty(domain, calibration);
        }

        TemporaryTerrainWorkspace.IntGrid coastDistance = chamferDistanceInside(
                workspace, dry, width, height);
        TemporaryTerrainWorkspace.LongGrid broadElevation = broadDryElevation(
                workspace,
                elevation,
                dry,
                width,
                height,
                calibration.smoothingRadiusCells());
        TemporaryTerrainWorkspace.ByteGrid eligible = workspace.byteGrid(area);

        int eligibleCount = 0;
        long minimumEligibleHeight = Long.MAX_VALUE;
        long maximumEligibleHeight = Long.MIN_VALUE;
        for (int cell = 0; cell < area; cell++) {
            if (!dry.getBoolean(cell)
                    || coastDistance.get(cell)
                            < calibration.minimumInteriorClearanceCells() * DISTANCE_SCALE
                    || elevation.get(cell) > calibration.maximumSourceElevationSubunits()) {
                continue;
            }
            eligible.setBoolean(cell, true);
            long heightValue = broadElevation.get(cell);
            minimumEligibleHeight = Math.min(minimumEligibleHeight, heightValue);
            maximumEligibleHeight = Math.max(maximumEligibleHeight, heightValue);
            eligibleCount++;
        }
        if (eligibleCount == 0) return empty(domain, calibration);

        int interiorCapacity = Math.toIntExact(
                (long) eligibleCount * recipe.maximumInteriorOccupancyPpm() / PPM);
        int desiredLakeCells = Math.min(calibration.targetLakeCells(), interiorCapacity);
        if (desiredLakeCells < calibration.minimumComponentCells()) {
            return empty(domain, calibration);
        }

        int supportTarget = Math.min(
                eligibleCount,
                Math.max(desiredLakeCells, Math.multiplyExact(desiredLakeCells, 3)));
        long threshold = kthValueThreshold(
                eligible,
                broadElevation,
                supportTarget,
                minimumEligibleHeight,
                maximumEligibleHeight,
                area);
        TemporaryTerrainWorkspace.ByteGrid support = workspace.byteGrid(area);
        for (int cell = 0; cell < area; cell++) {
            support.setBoolean(
                    cell,
                    eligible.getBoolean(cell) && broadElevation.get(cell) <= threshold);
        }

        TemporaryTerrainWorkspace.IntGrid supportWidth = chamferDistanceInside(
                workspace, support, width, height);
        int requiredHalfWidth = Math.max(
                2,
                (calibration.minimumComponentSpanCells() + 1) / 2);
        int requiredHalfWidthScaled = Math.multiplyExact(requiredHalfWidth, DISTANCE_SCALE);
        TemporaryTerrainWorkspace.ByteGrid broadCore = workspace.byteGrid(area);
        for (int cell = 0; cell < area; cell++) {
            broadCore.setBoolean(
                    cell,
                    support.getBoolean(cell) && supportWidth.get(cell) > requiredHalfWidthScaled);
        }

        TemporaryTerrainWorkspace.IntGrid distanceToCore = chamferDistanceFromTrue(
                workspace, broadCore, width, height);
        TemporaryTerrainWorkspace.ByteGrid regularized = workspace.byteGrid(area);
        for (int cell = 0; cell < area; cell++) {
            regularized.setBoolean(
                    cell,
                    support.getBoolean(cell)
                            && supportWidth.get(cell) > DISTANCE_SCALE
                            && distanceToCore.get(cell) <= requiredHalfWidthScaled);
        }

        TemporaryTerrainWorkspace.IntGrid componentIds = workspace.intGrid(area);
        componentIds.fill(-1);
        TemporaryTerrainWorkspace.IntVector queue = workspace.intVector(area);
        List<Component> valid = collectValidComponents(
                regularized,
                broadElevation,
                componentIds,
                queue,
                width,
                height,
                calibration);
        if (valid.isEmpty()) return empty(domain, calibration);

        valid.sort(Comparator
                .comparingDouble(Component::meanBroadElevation)
                .thenComparing(Comparator.comparingInt(Component::cellCount).reversed())
                .thenComparingInt(Component::id));

        Set<Integer> selected = new HashSet<>();
        int selectedBodies = 0;
        int selectedCells = 0;
        for (Component component : valid) {
            if (selectedBodies >= calibration.maximumLakeBodies()) break;
            if (selectedCells >= desiredLakeCells && selectedBodies > 0) break;
            selected.add(component.id());
            selectedBodies++;
            selectedCells += component.cellCount();
        }
        return runsFromSelected(domain, componentIds, selected, width, height, calibration);
    }

    private static List<Component> collectValidComponents(
            TemporaryTerrainWorkspace.ByteGrid regularized,
            TemporaryTerrainWorkspace.LongGrid broadElevation,
            TemporaryTerrainWorkspace.IntGrid componentIds,
            TemporaryTerrainWorkspace.IntVector queue,
            int width,
            int height,
            V15InlandLakeDomainCalibration calibration) {
        int area = Math.multiplyExact(width, height);
        List<Component> valid = new ArrayList<>();
        int nextId = 0;
        for (int start = 0; start < area; start++) {
            if (!regularized.getBoolean(start) || componentIds.get(start) >= 0) continue;
            int id = nextId++;
            int head = 0;
            int tail = 0;
            queue.set(tail++, start);
            componentIds.set(start, id);
            int cellCount = 0;
            long sumBroadElevation = 0L;
            int startX = start % width;
            int startY = start / width;
            int minX = startX;
            int maxX = startX;
            int minY = startY;
            int maxY = startY;

            while (head < tail) {
                int cell = queue.get(head++);
                int x = cell % width;
                int y = cell / width;
                cellCount++;
                sumBroadElevation += broadElevation.get(cell);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);

                if (x > 0) {
                    tail = enqueueRegularized(
                            cell - 1, id, regularized, componentIds, queue, tail);
                }
                if (x + 1 < width) {
                    tail = enqueueRegularized(
                            cell + 1, id, regularized, componentIds, queue, tail);
                }
                if (y > 0) {
                    tail = enqueueRegularized(
                            cell - width, id, regularized, componentIds, queue, tail);
                }
                if (y + 1 < height) {
                    tail = enqueueRegularized(
                            cell + width, id, regularized, componentIds, queue, tail);
                }
            }

            int spanX = maxX - minX + 1;
            int spanY = maxY - minY + 1;
            if (cellCount < calibration.minimumComponentCells()
                    || spanX < calibration.minimumComponentSpanCells()
                    || spanY < calibration.minimumComponentSpanCells()) {
                continue;
            }
            valid.add(new Component(
                    id,
                    cellCount,
                    sumBroadElevation,
                    minX,
                    maxX,
                    minY,
                    maxY));
        }
        return valid;
    }

    private static int enqueueRegularized(
            int cell,
            int id,
            TemporaryTerrainWorkspace.ByteGrid regularized,
            TemporaryTerrainWorkspace.IntGrid componentIds,
            TemporaryTerrainWorkspace.IntVector queue,
            int tail) {
        if (!regularized.getBoolean(cell) || componentIds.get(cell) >= 0) return tail;
        componentIds.set(cell, id);
        queue.set(tail, cell);
        return tail + 1;
    }

    private static long kthValueThreshold(
            TemporaryTerrainWorkspace.ByteGrid eligible,
            TemporaryTerrainWorkspace.LongGrid broadElevation,
            int rank,
            long minimum,
            long maximum,
            int area) {
        long low = minimum;
        long high = maximum;
        while (low < high) {
            long middle = low + (high - low) / 2L;
            int count = 0;
            for (int cell = 0; cell < area; cell++) {
                if (eligible.getBoolean(cell) && broadElevation.get(cell) <= middle) count++;
            }
            if (count >= rank) {
                high = middle;
            } else {
                low = middle + 1L;
            }
        }
        return low;
    }

    private static TemporaryTerrainWorkspace.LongGrid broadDryElevation(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.ByteGrid dry,
            int width,
            int height,
            int radius) throws IOException {
        int integralWidth = Math.addExact(width, 1);
        int integralArea = Math.multiplyExact(integralWidth, Math.addExact(height, 1));
        TemporaryTerrainWorkspace.LongGrid sum = workspace.longGrid(integralArea);
        TemporaryTerrainWorkspace.IntGrid count = workspace.intGrid(integralArea);
        for (int y = 0; y < height; y++) {
            long rowSum = 0L;
            int rowCount = 0;
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (dry.getBoolean(cell)) {
                    rowSum += elevation.get(cell);
                    rowCount++;
                }
                int integral = (y + 1) * integralWidth + x + 1;
                sum.set(integral, sum.get(y * integralWidth + x + 1) + rowSum);
                count.set(integral, count.get(y * integralWidth + x + 1) + rowCount);
            }
        }

        TemporaryTerrainWorkspace.LongGrid broad = workspace.longGrid(Math.multiplyExact(width, height));
        for (int cell = 0; cell < broad.size(); cell++) broad.set(cell, elevation.get(cell));
        for (int y = 0; y < height; y++) {
            int minY = Math.max(0, y - radius);
            int maxY = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (!dry.getBoolean(cell)) continue;
                int minX = Math.max(0, x - radius);
                int maxX = Math.min(width - 1, x + radius);
                long windowSum = rectangle(sum, integralWidth, minX, minY, maxX, maxY);
                int windowCount = Math.toIntExact(
                        rectangle(count, integralWidth, minX, minY, maxX, maxY));
                if (windowCount > 0) broad.set(cell, windowSum / windowCount);
            }
        }
        return broad;
    }

    private static long rectangle(
            TemporaryTerrainWorkspace.LongGrid integral,
            int stride,
            int minX,
            int minY,
            int maxX,
            int maxY) {
        int x1 = maxX + 1;
        int y1 = maxY + 1;
        return integral.get(y1 * stride + x1)
                - integral.get(minY * stride + x1)
                - integral.get(y1 * stride + minX)
                + integral.get(minY * stride + minX);
    }

    private static long rectangle(
            TemporaryTerrainWorkspace.IntGrid integral,
            int stride,
            int minX,
            int minY,
            int maxX,
            int maxY) {
        int x1 = maxX + 1;
        int y1 = maxY + 1;
        return (long) integral.get(y1 * stride + x1)
                - integral.get(minY * stride + x1)
                - integral.get(y1 * stride + minX)
                + integral.get(minY * stride + minX);
    }

    private static TemporaryTerrainWorkspace.IntGrid chamferDistanceInside(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.ByteGrid inside,
            int width,
            int height) throws IOException {
        TemporaryTerrainWorkspace.IntGrid distance = workspace.intGrid(Math.multiplyExact(width, height));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                distance.set(
                        cell,
                        inside.getBoolean(cell)
                                        && x > 0
                                        && x + 1 < width
                                        && y > 0
                                        && y + 1 < height
                                ? INFINITE_DISTANCE
                                : 0);
            }
        }
        chamferPasses(distance, width, height);
        return distance;
    }

    private static TemporaryTerrainWorkspace.IntGrid chamferDistanceFromTrue(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.ByteGrid source,
            int width,
            int height) throws IOException {
        TemporaryTerrainWorkspace.IntGrid distance = workspace.intGrid(Math.multiplyExact(width, height));
        boolean any = false;
        for (int cell = 0; cell < Math.multiplyExact(width, height); cell++) {
            if (source.getBoolean(cell)) {
                distance.set(cell, 0);
                any = true;
            } else {
                distance.set(cell, INFINITE_DISTANCE);
            }
        }
        if (any) chamferPasses(distance, width, height);
        return distance;
    }

    private static void chamferPasses(
            TemporaryTerrainWorkspace.IntGrid distance,
            int width,
            int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (distance.get(cell) == 0) continue;
                int best = distance.get(cell);
                if (x > 0) best = Math.min(best, plus(distance.get(cell - 1), CARDINAL_DISTANCE));
                if (y > 0) best = Math.min(best, plus(distance.get(cell - width), CARDINAL_DISTANCE));
                if (x > 0 && y > 0) {
                    best = Math.min(best, plus(distance.get(cell - width - 1), DIAGONAL_DISTANCE));
                }
                if (x + 1 < width && y > 0) {
                    best = Math.min(best, plus(distance.get(cell - width + 1), DIAGONAL_DISTANCE));
                }
                distance.set(cell, best);
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int cell = y * width + x;
                if (distance.get(cell) == 0) continue;
                int best = distance.get(cell);
                if (x + 1 < width) best = Math.min(best, plus(distance.get(cell + 1), CARDINAL_DISTANCE));
                if (y + 1 < height) best = Math.min(best, plus(distance.get(cell + width), CARDINAL_DISTANCE));
                if (x + 1 < width && y + 1 < height) {
                    best = Math.min(best, plus(distance.get(cell + width + 1), DIAGONAL_DISTANCE));
                }
                if (x > 0 && y + 1 < height) {
                    best = Math.min(best, plus(distance.get(cell + width - 1), DIAGONAL_DISTANCE));
                }
                distance.set(cell, best);
            }
        }
    }

    private static int plus(int distance, int increment) {
        if (distance >= INFINITE_DISTANCE - increment) return INFINITE_DISTANCE;
        return distance + increment;
    }

    private static int materializeBase(
            ContinuumScalarPageSource base,
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.ByteGrid dry,
            int width,
            int height) {
        int dryLandCells = 0;
        for (int minY = 0; minY < height; minY += BASE_STAGING_ROWS) {
            int rows = Math.min(BASE_STAGING_ROWS, height - minY);
            ContinuumScalarPage page = base.materialize(
                    new ContinuumSampleWindow(0L, minY, width, rows, 1L));
            for (int localY = 0; localY < rows; localY++) {
                int row = (minY + localY) * width;
                for (int x = 0; x < width; x++) {
                    int cell = row + x;
                    long value = Math.round(page.sample(x, localY));
                    elevation.set(cell, value);
                    boolean isDry = value > 0L;
                    dry.setBoolean(cell, isDry);
                    if (isDry) dryLandCells++;
                }
            }
        }
        return dryLandCells;
    }

    private static V15InlandLakeDomainPlan runsFromSelected(
            ContinuumWorldDomain domain,
            TemporaryTerrainWorkspace.IntGrid componentIds,
            Set<Integer> selected,
            int width,
            int height,
            V15InlandLakeDomainCalibration calibration) {
        List<Long> starts = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        int lakeCellCount = 0;
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                int cell = y * width + x;
                if (!selected.contains(componentIds.get(cell))) {
                    x++;
                    continue;
                }
                int startX = x;
                while (x + 1 < width
                        && selected.contains(componentIds.get(y * width + x + 1))) {
                    x++;
                }
                int endX = x;
                long rowOffset = Math.multiplyExact((long) y, domain.width());
                starts.add(Math.addExact(rowOffset, startX));
                ends.add(Math.addExact(rowOffset, endX));
                lakeCellCount = Math.addExact(lakeCellCount, endX - startX + 1);
                x++;
            }
        }
        if (lakeCellCount == 0) return empty(domain, calibration);
        long[] runStarts = new long[starts.size()];
        long[] runEnds = new long[ends.size()];
        for (int index = 0; index < starts.size(); index++) {
            runStarts[index] = starts.get(index);
            runEnds[index] = ends.get(index);
        }
        return new V15InlandLakeDomainPlan(
                domain, runStarts, runEnds, lakeCellCount, calibration);
    }

    private static V15InlandLakeDomainPlan empty(
            ContinuumWorldDomain domain,
            V15InlandLakeDomainCalibration calibration) {
        return new V15InlandLakeDomainPlan(domain, new long[0], new long[0], 0, calibration);
    }

    private static void validateInputs(
            ContinuumWorldDomain domain,
            ContinuumScalarPageSource base,
            V15InlandLakeDomainRecipe recipe) {
        if (domain == null || base == null || recipe == null) {
            throw new IllegalArgumentException("V15 inland-lake domain inputs must not be null");
        }
        if (!domain.equals(base.domain())) {
            throw new IllegalArgumentException("V15 continental base must match the lake domain");
        }
        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        Math.multiplyExact(width, height);
    }

    private record Component(
            int id,
            int cellCount,
            long sumBroadElevation,
            int minX,
            int maxX,
            int minY,
            int maxY) {
        double meanBroadElevation() {
            return sumBroadElevation / (double) cellCount;
        }
    }
}
