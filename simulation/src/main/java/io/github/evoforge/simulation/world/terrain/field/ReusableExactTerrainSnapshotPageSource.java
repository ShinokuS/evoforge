package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V15GenerationProfiler;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Reuses an exact finite-stage result without keeping a resident world-sized Java array.
 *
 * <p>For small worlds the existing in-memory bounded snapshot is retained. For moderately sized
 * exact-oracle worlds this wrapper computes the wrapped stage once, streams its result into a
 * temporary file, and serves all later page requests from that immutable snapshot. This changes
 * only execution reuse: the wrapped historical V15 stage remains the sole terrain author.</p>
 *
 * <p>The bridge is intentionally capped. It is not the final Continuum large-world model; its job is
 * to eliminate accidental repeated full-domain execution while the genuinely global historical
 * decisions are being reformulated.</p>
 */
public final class ReusableExactTerrainSnapshotPageSource implements ContinuumScalarPageSource {
    public static final long MAX_FILE_SNAPSHOT_BYTES = 32L * 1024L * 1024L;
    private static final int WRITE_BUFFER_BYTES = 64 * 1024;
    private static final Cleaner CLEANER = Cleaner.create();

    private final String stageName;
    private final ContinuumScalarPageSource source;
    private final ContinuumWorldDomain domain;
    private final int worldWidth;
    private final int worldHeight;
    private volatile Snapshot snapshot;

    private ReusableExactTerrainSnapshotPageSource(
            String stageName,
            ContinuumScalarPageSource source) {
        this.stageName = stageName;
        this.source = source;
        this.domain = source.domain();
        this.worldWidth = Math.toIntExact(domain.width());
        this.worldHeight = Math.toIntExact(domain.height());
    }

    public static ContinuumScalarPageSource captureIfPractical(
            String stageName,
            ContinuumScalarPageSource source) {
        if (stageName == null || stageName.isBlank() || source == null) {
            throw new IllegalArgumentException("reusable exact snapshot inputs must not be null/blank");
        }

        ContinuumScalarPageSource bounded = BoundedExactTerrainSnapshotPageSource.captureIfBounded(source);
        if (bounded != source) return bounded;

        ContinuumWorldDomain domain = source.domain();
        long cells;
        long bytes;
        try {
            cells = Math.multiplyExact(domain.width(), domain.height());
            bytes = Math.multiplyExact(cells, (long) Double.BYTES);
            Math.toIntExact(domain.width());
            Math.toIntExact(domain.height());
        } catch (ArithmeticException exception) {
            return source;
        }
        if (bytes > MAX_FILE_SNAPSHOT_BYTES) return source;
        return new ReusableExactTerrainSnapshotPageSource(stageName, source);
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        return ensureSnapshot().read(window);
    }

    private Snapshot ensureSnapshot() {
        Snapshot current = snapshot;
        if (current != null) return current;
        synchronized (this) {
            current = snapshot;
            if (current != null) return current;
            long started = System.nanoTime();
            current = buildSnapshot();
            snapshot = current;
            long cells = Math.multiplyExact(domain.width(), domain.height());
            V15GenerationProfiler.record(
                    stageName + ":snapshot",
                    cells,
                    System.nanoTime() - started);
            return current;
        }
    }

    private Snapshot buildSnapshot() {
        ContinuumSampleWindow whole = new ContinuumSampleWindow(
                0L,
                0L,
                worldWidth,
                worldHeight,
                1L);
        ContinuumScalarPage page = source.materialize(whole);
        Path path = null;
        FileChannel channel = null;
        try {
            path = Files.createTempFile("evoforge-v15-stage-", ".terrain");
            channel = FileChannel.open(
                    path,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            ByteBuffer buffer = ByteBuffer.allocate(WRITE_BUFFER_BYTES).order(ByteOrder.BIG_ENDIAN);
            long filePosition = 0L;
            for (int y = 0; y < worldHeight; y++) {
                for (int x = 0; x < worldWidth; x++) {
                    if (buffer.remaining() < Double.BYTES) {
                        filePosition = flush(channel, buffer, filePosition);
                    }
                    buffer.putDouble(page.sample(x, y));
                }
            }
            flush(channel, buffer, filePosition);
            return new Snapshot(path, channel, worldWidth);
        } catch (IOException exception) {
            closeQuietly(channel);
            deleteQuietly(path);
            throw new IllegalStateException("unable to retain exact V15 stage snapshot", exception);
        }
    }

    private static long flush(FileChannel channel, ByteBuffer buffer, long filePosition)
            throws IOException {
        buffer.flip();
        long position = filePosition;
        while (buffer.hasRemaining()) {
            int written = channel.write(buffer, position);
            if (written == 0) continue;
            position += written;
        }
        buffer.clear();
        return position;
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside reusable exact terrain snapshot");
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) return;
        try {
            channel.close();
        } catch (IOException ignored) {
            // Best-effort cleanup of disposable exact-stage storage.
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup of disposable exact-stage storage.
        }
    }

    private static final class Snapshot {
        private final FileChannel channel;
        private final int worldWidth;
        @SuppressWarnings("unused")
        private final Cleaner.Cleanable cleanable;

        private Snapshot(Path path, FileChannel channel, int worldWidth) {
            this.channel = channel;
            this.worldWidth = worldWidth;
            this.cleanable = CLEANER.register(this, new Cleanup(path, channel));
        }

        private ContinuumScalarPage read(ContinuumSampleWindow window) {
            double[] result = new double[Math.multiplyExact(window.width(), window.height())];
            try {
                if (window.step() == 1L) {
                    readContiguousRows(window, result);
                } else {
                    readSparse(window, result);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("unable to read reusable exact terrain snapshot", exception);
            }
            return new ContinuumScalarPage(window, result);
        }

        private void readContiguousRows(ContinuumSampleWindow window, double[] result)
                throws IOException {
            int rowBytes = Math.multiplyExact(window.width(), Double.BYTES);
            ByteBuffer row = ByteBuffer.allocate(rowBytes).order(ByteOrder.BIG_ENDIAN);
            int cursor = 0;
            for (int sampleY = 0; sampleY < window.height(); sampleY++) {
                long worldY = window.yAt(sampleY);
                long firstCell = Math.addExact(
                        Math.multiplyExact(worldY, (long) worldWidth),
                        window.minX());
                long byteOffset = Math.multiplyExact(firstCell, (long) Double.BYTES);
                readFully(channel, row, byteOffset);
                row.flip();
                while (row.hasRemaining()) result[cursor++] = row.getDouble();
                row.clear();
            }
        }

        private void readSparse(ContinuumSampleWindow window, double[] result) throws IOException {
            ByteBuffer value = ByteBuffer.allocate(Double.BYTES).order(ByteOrder.BIG_ENDIAN);
            int cursor = 0;
            for (int y = 0; y < window.height(); y++) {
                long worldY = window.yAt(y);
                for (int x = 0; x < window.width(); x++) {
                    long worldX = window.xAt(x);
                    long cell = Math.addExact(
                            Math.multiplyExact(worldY, (long) worldWidth),
                            worldX);
                    long byteOffset = Math.multiplyExact(cell, (long) Double.BYTES);
                    readFully(channel, value, byteOffset);
                    value.flip();
                    result[cursor++] = value.getDouble();
                    value.clear();
                }
            }
        }

        private static void readFully(FileChannel channel, ByteBuffer buffer, long offset)
                throws IOException {
            long position = offset;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, position);
                if (read < 0) throw new IOException("unexpected end of exact terrain snapshot");
                if (read == 0) continue;
                position += read;
            }
        }
    }

    private static final class Cleanup implements Runnable {
        private final Path path;
        private final FileChannel channel;

        private Cleanup(Path path, FileChannel channel) {
            this.path = path;
            this.channel = channel;
        }

        @Override
        public void run() {
            closeQuietly(channel);
            deleteQuietly(path);
        }
    }
}
