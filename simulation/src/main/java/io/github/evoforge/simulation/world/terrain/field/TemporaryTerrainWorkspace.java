package io.github.evoforge.simulation.world.terrain.field;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Disposable file-backed primitive storage for parity-first historical global passes.
 *
 * <p>It deliberately does not make any whole-world raster authoritative runtime state. Files exist
 * only while one bounded materialization is being evaluated and are deleted on close. This class is
 * not the future large-world storage design; it is the exact-semantics bridge used while V12-V15 are
 * proven cell-for-cell against the historical dense oracle.</p>
 */
final class TemporaryTerrainWorkspace implements AutoCloseable {
    private final List<Path> paths = new ArrayList<>();
    private final List<FileChannel> channels = new ArrayList<>();

    LongGrid longGrid(int cells) throws IOException {
        return new LongGrid(map(cells, Long.BYTES), cells);
    }

    IntGrid intGrid(int cells) throws IOException {
        return new IntGrid(map(cells, Integer.BYTES), cells);
    }

    ByteGrid byteGrid(int cells) throws IOException {
        return new ByteGrid(map(cells, Byte.BYTES), cells);
    }

    IntVector intVector(int capacity) throws IOException {
        return new IntVector(map(capacity, Integer.BYTES), capacity);
    }

    LongVector longVector(int capacity) throws IOException {
        return new LongVector(map(capacity, Long.BYTES), capacity);
    }

    private MappedByteBuffer map(int cells, int bytesPerCell) throws IOException {
        if (cells < 0) throw new IllegalArgumentException("cell count must not be negative");
        long bytes = Math.multiplyExact((long) cells, bytesPerCell);
        if (bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "parity workspace mapping exceeds one Java buffer; large-world segmentation is future work");
        }
        Path path = Files.createTempFile("evoforge-terrain-", ".bin");
        FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        if (bytes > 0L) {
            channel.position(bytes - 1L);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }
        MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0L, bytes);
        buffer.order(ByteOrder.BIG_ENDIAN);
        paths.add(path);
        channels.add(channel);
        return buffer;
    }

    @Override
    public void close() {
        for (FileChannel channel : channels) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // Disposable parity storage; deletion below remains best-effort.
            }
        }
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Best-effort cleanup of disposable workspace files.
            }
        }
    }

    static final class LongGrid {
        private final MappedByteBuffer buffer;
        private final int cells;

        private LongGrid(MappedByteBuffer buffer, int cells) {
            this.buffer = buffer;
            this.cells = cells;
        }

        int size() {
            return cells;
        }

        long get(int index) {
            require(index, cells);
            return buffer.getLong(Math.multiplyExact(index, Long.BYTES));
        }

        void set(int index, long value) {
            require(index, cells);
            buffer.putLong(Math.multiplyExact(index, Long.BYTES), value);
        }

        void fill(long value) {
            for (int index = 0; index < cells; index++) set(index, value);
        }

        void copyFrom(LongGrid source) {
            if (source.cells != cells) throw new IllegalArgumentException("grid sizes must match");
            for (int index = 0; index < cells; index++) set(index, source.get(index));
        }
    }

    static final class IntGrid {
        private final MappedByteBuffer buffer;
        private final int cells;

        private IntGrid(MappedByteBuffer buffer, int cells) {
            this.buffer = buffer;
            this.cells = cells;
        }

        int size() {
            return cells;
        }

        int get(int index) {
            require(index, cells);
            return buffer.getInt(Math.multiplyExact(index, Integer.BYTES));
        }

        void set(int index, int value) {
            require(index, cells);
            buffer.putInt(Math.multiplyExact(index, Integer.BYTES), value);
        }

        void fill(int value) {
            for (int index = 0; index < cells; index++) set(index, value);
        }

        void copyFrom(IntGrid source) {
            if (source.cells != cells) throw new IllegalArgumentException("grid sizes must match");
            for (int index = 0; index < cells; index++) set(index, source.get(index));
        }
    }

    static final class ByteGrid {
        private final MappedByteBuffer buffer;
        private final int cells;

        private ByteGrid(MappedByteBuffer buffer, int cells) {
            this.buffer = buffer;
            this.cells = cells;
        }

        byte get(int index) {
            require(index, cells);
            return buffer.get(index);
        }

        boolean getBoolean(int index) {
            return get(index) != 0;
        }

        void set(int index, byte value) {
            require(index, cells);
            buffer.put(index, value);
        }

        void setBoolean(int index, boolean value) {
            set(index, value ? (byte) 1 : (byte) 0);
        }

        void fill(byte value) {
            for (int index = 0; index < cells; index++) set(index, value);
        }
    }

    static final class IntVector {
        private final MappedByteBuffer buffer;
        private final int capacity;

        private IntVector(MappedByteBuffer buffer, int capacity) {
            this.buffer = buffer;
            this.capacity = capacity;
        }

        int get(int index) {
            require(index, capacity);
            return buffer.getInt(Math.multiplyExact(index, Integer.BYTES));
        }

        void set(int index, int value) {
            require(index, capacity);
            buffer.putInt(Math.multiplyExact(index, Integer.BYTES), value);
        }
    }

    static final class LongVector {
        private final MappedByteBuffer buffer;
        private final int capacity;

        private LongVector(MappedByteBuffer buffer, int capacity) {
            this.buffer = buffer;
            this.capacity = capacity;
        }

        long get(int index) {
            require(index, capacity);
            return buffer.getLong(Math.multiplyExact(index, Long.BYTES));
        }

        void set(int index, long value) {
            require(index, capacity);
            buffer.putLong(Math.multiplyExact(index, Long.BYTES), value);
        }
    }

    private static void require(int index, int size) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
    }
}
