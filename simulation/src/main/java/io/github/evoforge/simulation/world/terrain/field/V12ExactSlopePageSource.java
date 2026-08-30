package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.LegacyV12Noise;
import io.github.evoforge.simulation.world.terrain.genesis.V12ContinuumSlopeCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V12LandRankPlan;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainRecipe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Exact Continuum execution of the historical V12 in-place slope relaxation.
 *
 * <p>The old V12 algorithm performs four ordered full-domain sweeps. A finite request halo cannot
 * prove equivalence because one sweep may propagate changes through an arbitrarily long connected
 * land chain. This implementation therefore preserves the historical iteration order exactly while
 * changing only intermediate storage: complete pass state is spooled to temporary files and only
 * two terrain rows are resident on the Java heap. The returned value is still only the requested
 * {@link ContinuumSampleWindow}; no generated world raster becomes authoritative runtime state.</p>
 *
 * <p>This is intentionally a parity-first execution model, not a large-world optimization.</p>
 */
public final class V12ExactSlopePageSource implements ContinuumScalarPageSource {
    private static final int POTENTIAL_BIN_COUNT = LegacyV12Noise.SAMPLE_MAX + 2;

    private final ContinuumWorldDomain domain;
    private final V12UnrelaxedLandElevationField unrelaxed;
    private final V12LandRankPlan land;
    private final V12ContinuumSlopeCalibration slope;
    private final V12TerrainRecipe recipe;
    private final int minimumZCells;

    public V12ExactSlopePageSource(
            ContinuumWorldDomain domain,
            V12UnrelaxedLandElevationField unrelaxed,
            V12LandRankPlan land,
            V12ContinuumSlopeCalibration slope,
            V12TerrainRecipe recipe,
            int minimumZCells) {
        if (domain == null || unrelaxed == null || land == null || slope == null || recipe == null) {
            throw new IllegalArgumentException("V12 exact slope inputs must not be null");
        }
        if (minimumZCells >= 0) {
            throw new IllegalArgumentException("minimumZCells must be below sea level");
        }
        this.domain = domain;
        this.unrelaxed = unrelaxed;
        this.land = land;
        this.slope = slope;
        this.recipe = recipe;
        this.minimumZCells = minimumZCells;
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        Path firstPath = null;
        Path secondPath = null;
        try {
            firstPath = Files.createTempFile("evoforge-v12-a-", ".terrain");
            secondPath = Files.createTempFile("evoforge-v12-b-", ".terrain");
            try (FileChannel first = open(firstPath); FileChannel second = open(secondPath)) {
                initializeHistoricalUnrelaxedField(first);
                FileChannel source = first;
                FileChannel destination = second;
                for (int pass = 0; pass < recipe.relaxationPasses(); pass++) {
                    destination.truncate(0L);
                    if ((pass & 1) == 0) {
                        sweepForward(source, destination);
                    } else {
                        sweepReverse(source, destination);
                    }
                    FileChannel swap = source;
                    source = destination;
                    destination = swap;
                }
                return readWindow(source, window);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("unable to execute exact V12 slope sweeps", exception);
        } finally {
            deleteQuietly(firstPath);
            deleteQuietly(secondPath);
        }
    }

    private void initializeHistoricalUnrelaxedField(FileChannel channel) throws IOException {
        int width = Math.toIntExact(domain.width());
        long[] histogram = new long[POTENTIAL_BIN_COUNT];
        for (long y = 0L; y < domain.height(); y++) {
            for (long x = 0L; x < domain.width(); x++) {
                histogram[potentialBin(land.potentialAt(x, y))]++;
            }
        }

        long[] greater = new long[POTENTIAL_BIN_COUNT];
        long running = 0L;
        for (int bin = POTENTIAL_BIN_COUNT - 1; bin >= 0; bin--) {
            greater[bin] = running;
            running = Math.addExact(running, histogram[bin]);
        }
        long[] seen = new long[POTENTIAL_BIN_COUNT];
        long area = Math.multiplyExact(domain.width(), domain.height());
        long waterCount = area - land.landCount();
        long oceanAmplitude = Math.multiplyExact(
                -(long) minimumZCells,
                TerrainElevationField.SUBUNITS_PER_CELL);

        for (long y = 0L; y < domain.height(); y++) {
            long[] row = new long[width];
            for (int x = 0; x < width; x++) {
                int potential = land.potentialAt(x, y);
                int bin = potentialBin(potential);
                long sortedRank = Math.addExact(greater[bin], seen[bin]++);
                if (land.isLand(x, y)) {
                    row[x] = unrelaxed.elevationSubunitsAt(x, y);
                } else {
                    long waterOrdinal = sortedRank - land.landCount();
                    if (waterOrdinal < 0L || waterOrdinal >= waterCount) {
                        throw new IllegalStateException("historical V12 water rank fell outside water partition");
                    }
                    row[x] = -positiveRankHeight(waterOrdinal, waterCount, oceanAmplitude);
                }
            }
            writeRow(channel, y, row);
        }
    }

    private void sweepForward(FileChannel source, FileChannel destination) throws IOException {
        int width = Math.toIntExact(domain.width());
        long height = domain.height();
        long[] current = readRow(source, 0L, width);
        boolean[] currentLand = landRow(0L, width);
        long[] next = height > 1L ? readRow(source, 1L, width) : null;
        boolean[] nextLand = height > 1L ? landRow(1L, width) : null;

        for (long y = 0L; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!currentLand[x]) continue;
                if (x + 1 < width && currentLand[x + 1]) {
                    relaxPair(current, x, current, x + 1);
                }
                if (next != null && nextLand[x]) {
                    relaxPair(current, x, next, x);
                }
            }
            writeRow(destination, y, current);
            if (y + 1L >= height) continue;
            current = next;
            currentLand = nextLand;
            long followingY = y + 2L;
            next = followingY < height ? readRow(source, followingY, width) : null;
            nextLand = followingY < height ? landRow(followingY, width) : null;
        }
    }

    private void sweepReverse(FileChannel source, FileChannel destination) throws IOException {
        int width = Math.toIntExact(domain.width());
        long height = domain.height();
        long lastY = height - 1L;
        long[] current = readRow(source, lastY, width);
        boolean[] currentLand = landRow(lastY, width);
        long[] previous = lastY > 0L ? readRow(source, lastY - 1L, width) : null;
        boolean[] previousLand = lastY > 0L ? landRow(lastY - 1L, width) : null;

        for (long y = lastY; y >= 0L; y--) {
            for (int x = width - 1; x >= 0; x--) {
                if (!currentLand[x]) continue;
                if (x > 0 && currentLand[x - 1]) {
                    relaxPair(current, x, current, x - 1);
                }
                if (previous != null && previousLand[x]) {
                    relaxPair(current, x, previous, x);
                }
            }
            writeRow(destination, y, current);
            if (y == 0L) break;
            current = previous;
            currentLand = previousLand;
            long followingY = y - 2L;
            previous = followingY >= 0L ? readRow(source, followingY, width) : null;
            previousLand = followingY >= 0L ? landRow(followingY, width) : null;
        }
    }

    private void relaxPair(long[] firstRow, int first, long[] secondRow, int second) {
        long difference = firstRow[first] - secondRow[second];
        long magnitude = Math.abs(difference);
        long maximumStep = slope.maximumStepSubunits();
        if (magnitude <= maximumStep) return;
        long excess = magnitude - maximumStep;
        long firstCorrection = (excess + 1L) / 2L;
        long secondCorrection = excess - firstCorrection;
        if (difference > 0L) {
            firstRow[first] = clampLandHeight(firstRow[first] - firstCorrection);
            secondRow[second] = clampLandHeight(secondRow[second] + secondCorrection);
        } else {
            firstRow[first] = clampLandHeight(firstRow[first] + firstCorrection);
            secondRow[second] = clampLandHeight(secondRow[second] - secondCorrection);
        }
    }

    private long clampLandHeight(long value) {
        return Math.max(1L, Math.min(slope.maximumLandHeightSubunits(), value));
    }

    private boolean[] landRow(long y, int width) {
        boolean[] result = new boolean[width];
        for (int x = 0; x < width; x++) result[x] = land.isLand(x, y);
        return result;
    }

    private ContinuumScalarPage readWindow(FileChannel channel, ContinuumSampleWindow window)
            throws IOException {
        int worldWidth = Math.toIntExact(domain.width());
        double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            long worldY = window.yAt(sampleY);
            long[] row = readRow(channel, worldY, worldWidth);
            for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                samples[cursor++] = row[Math.toIntExact(window.xAt(sampleX))];
            }
        }
        return new ContinuumScalarPage(window, samples);
    }

    private static FileChannel open(Path path) throws IOException {
        return FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static long[] readRow(FileChannel channel, long rowIndex, int width) throws IOException {
        int byteCount = Math.multiplyExact(width, Long.BYTES);
        ByteBuffer buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.BIG_ENDIAN);
        long offset = Math.multiplyExact(rowIndex, (long) byteCount);
        long position = offset;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) throw new IOException("unexpected end of V12 terrain backing file");
            if (read == 0) continue;
            position += read;
        }
        buffer.flip();
        long[] row = new long[width];
        for (int x = 0; x < width; x++) row[x] = buffer.getLong();
        return row;
    }

    private static void writeRow(FileChannel channel, long rowIndex, long[] row) throws IOException {
        int byteCount = Math.multiplyExact(row.length, Long.BYTES);
        ByteBuffer buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.BIG_ENDIAN);
        for (long value : row) buffer.putLong(value);
        buffer.flip();
        long offset = Math.multiplyExact(rowIndex, (long) byteCount);
        long position = offset;
        while (buffer.hasRemaining()) {
            int written = channel.write(buffer, position);
            if (written == 0) continue;
            position += written;
        }
    }

    private static long positiveRankHeight(long waterOrdinal, long waterCount, long amplitude) {
        if (waterCount <= 0L) return 0L;
        if (waterCount == 1L) return Math.max(1L, amplitude);
        return 1L + ((amplitude - 1L) * waterOrdinal) / (waterCount - 1L);
    }

    private static int potentialBin(int potential) {
        int bin = potential + 1;
        if (bin < 0 || bin >= POTENTIAL_BIN_COUNT) {
            throw new IllegalArgumentException("V12 potential lies outside historical rank domain");
        }
        return bin;
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside V12 terrain domain");
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary parity backing is disposable; generation result has already been copied out.
        }
    }
}
