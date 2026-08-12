package io.github.evoforge.visualizer.visual;

import java.util.Arrays;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/**
 * Resolves landscape presentation from geometry rather than absolute Z values.
 *
 * <p>The selected standing plane is a horizontal cut. Terrain may intersect
 * that cut as solid body, support it as the current surface, or be visible
 * below through an actually open column. Air exposure is derived from
 * connectivity to sky-open volume, so cave mouths, deep chambers, shafts and
 * tall caverns all use the same rules.</p>
 */
public final class LandscapeSliceResolver {

    public enum Kind {
        SOLID_BODY,
        CURRENT_SURFACE,
        LOWER_SURFACE,
        EMPTY
    }

    public record Cell(
            Kind kind,
            int terrainZ,
            int dropDepth,
            int bodyDepth,
            int ceilingDistance,
            int coverDepth,
            int exposureDistance,
            Shape shape) {

        public Cell {
            if (kind == null) {
                throw new IllegalArgumentException("kind must not be null");
            }
            if (dropDepth < 0
                    || bodyDepth < 0
                    || ceilingDistance < 0
                    || coverDepth < 0
                    || exposureDistance < 0) {
                throw new IllegalArgumentException(
                        "visibility distances must not be negative");
            }
            if (kind == Kind.LOWER_SURFACE && dropDepth <= 0) {
                throw new IllegalArgumentException(
                        "lower surface depth must be positive");
            }
            if (kind != Kind.LOWER_SURFACE && dropDepth != 0) {
                throw new IllegalArgumentException(
                        "non-lower slice cell must have drop depth zero");
            }
            if (kind == Kind.SOLID_BODY && bodyDepth <= 0) {
                throw new IllegalArgumentException(
                        "solid body depth must be positive");
            }
            if (kind != Kind.SOLID_BODY && bodyDepth != 0) {
                throw new IllegalArgumentException(
                        "non-solid slice cell must have body depth zero");
            }
        }

        public static Cell empty() {
            return new Cell(
                    Kind.EMPTY,
                    Integer.MIN_VALUE,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null);
        }

        public boolean covered() {
            return coverDepth > 0;
        }
    }

    /** One camera-local exposure field reused by every visible XY cell. */
    public final class Analysis {

        private final int selectedStandingZ;
        private final int maxLowerDepth;
        private final int maxExposureDistance;
        private final ExposureField exposure;

        private Analysis(
                int minX,
                int maxX,
                int minY,
                int maxY,
                int selectedStandingZ,
                int maxLowerDepth,
                int maxExposureDistance) {

            this.selectedStandingZ = selectedStandingZ;
            this.maxLowerDepth = maxLowerDepth;
            this.maxExposureDistance = maxExposureDistance;
            exposure = buildExposureField(
                    minX,
                    maxX,
                    minY,
                    maxY,
                    selectedStandingZ,
                    maxLowerDepth,
                    maxExposureDistance);
        }

        public Cell resolve(
                int x,
                int y) {

            if (view.terrain().contains(x, y, selectedStandingZ)) {
                return new Cell(
                        Kind.SOLID_BODY,
                        selectedStandingZ,
                        0,
                        terrainBodyDepth(x, y, selectedStandingZ),
                        0,
                        0,
                        0,
                        view.geometry().find(x, y, selectedStandingZ));
            }

            if (isCurrentSurface(x, y, selectedStandingZ)) {
                int supportTerrainZ = selectedStandingZ - 1;
                return surfaceCell(
                        Kind.CURRENT_SURFACE,
                        x,
                        y,
                        supportTerrainZ,
                        0);
            }

            for (int depth = 1; depth <= maxLowerDepth; depth++) {
                int interveningZ = selectedStandingZ - depth;
                if (volume.solid(x, y, interveningZ)
                        || volume.opaque(x, y, interveningZ)) {
                    return Cell.empty();
                }

                int terrainZ = selectedStandingZ - depth - 1;
                if (view.terrain().contains(x, y, terrainZ)) {
                    return surfaceCell(
                            Kind.LOWER_SURFACE,
                            x,
                            y,
                            terrainZ,
                            depth);
                }
            }

            return Cell.empty();
        }

        private Cell surfaceCell(
                Kind kind,
                int x,
                int y,
                int terrainZ,
                int dropDepth) {

            int airZ = terrainZ + 1;
            Cover cover = coverAt(x, y, airZ);
            int exposureDistance = exposure.distanceAt(
                    x,
                    y,
                    airZ,
                    maxExposureDistance + 1);

            return new Cell(
                    kind,
                    terrainZ,
                    dropDepth,
                    0,
                    cover.ceilingDistance(),
                    cover.depth(),
                    exposureDistance,
                    view.geometry().find(x, y, terrainZ));
        }
    }

    private final SimulationView view;
    private final VisibilityVolumeLookup volume;

    public LandscapeSliceResolver(
            SimulationView view) {

        this(view, new TerrainVisibilityVolume(view));
    }

    public LandscapeSliceResolver(
            SimulationView view,
            VisibilityVolumeLookup volume) {

        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (volume == null) {
            throw new IllegalArgumentException("volume must not be null");
        }
        this.view = view;
        this.volume = volume;
    }

    /**
     * Cheap structural query for overlays that only need the active standing
     * surface and must not rebuild the camera-local exposure field.
     */
    public boolean isCurrentSurface(
            int x,
            int y,
            int selectedStandingZ) {

        if (selectedStandingZ == Integer.MIN_VALUE) {
            return false;
        }

        return !view.terrain().contains(x, y, selectedStandingZ)
                && view.terrain().contains(x, y, selectedStandingZ - 1);
    }

    public Analysis analyze(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ,
            int maxLowerDepth,
            int maxExposureDistance) {

        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("invalid XY analysis bounds");
        }
        if (maxLowerDepth < 0) {
            throw new IllegalArgumentException(
                    "maxLowerDepth must not be negative");
        }
        if (maxExposureDistance < 0) {
            throw new IllegalArgumentException(
                    "maxExposureDistance must not be negative");
        }

        return new Analysis(
                minX,
                maxX,
                minY,
                maxY,
                selectedStandingZ,
                maxLowerDepth,
                maxExposureDistance);
    }

    /** Convenience for narrow tests and one-cell tooling. */
    public Cell resolve(
            int x,
            int y,
            int selectedStandingZ,
            int maxLowerDepth) {

        return analyze(
                x,
                x,
                y,
                y,
                selectedStandingZ,
                maxLowerDepth,
                0)
                .resolve(x, y);
    }

    private ExposureField buildExposureField(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ,
            int maxLowerDepth,
            int maxExposureDistance) {

        int expandedMinX = safeAdd(minX, -maxExposureDistance);
        int expandedMaxX = safeAdd(maxX, maxExposureDistance);
        int expandedMinY = safeAdd(minY, -maxExposureDistance);
        int expandedMaxY = safeAdd(maxY, maxExposureDistance);
        int verticalMargin = maxLowerDepth + maxExposureDistance + 1;
        int minZ = safeAdd(selectedStandingZ, -verticalMargin);
        int maxZ = safeAdd(selectedStandingZ, maxExposureDistance + 1);

        ExposureField field = new ExposureField(
                expandedMinX,
                expandedMaxX,
                expandedMinY,
                expandedMaxY,
                minZ,
                maxZ);
        int[] queue = new int[field.size()];
        int head = 0;
        int tail = 0;

        for (long lx = expandedMinX; lx <= expandedMaxX; lx++) {
            int x = (int) lx;
            for (long ly = expandedMinY; ly <= expandedMaxY; ly++) {
                int y = (int) ly;
                TopOpaque top = findTopOpaque(x, y);

                for (long lz = minZ; lz <= maxZ; lz++) {
                    int z = (int) lz;
                    if (!openForExposure(x, y, z)) {
                        continue;
                    }
                    if (top.present() && z <= top.z()) {
                        continue;
                    }

                    int index = field.indexOf(x, y, z);
                    field.setDistance(index, 0);
                    queue[tail++] = index;
                }
            }
        }

        while (head < tail) {
            int currentIndex = queue[head++];
            int distance = field.distanceAt(currentIndex);
            if (distance >= maxExposureDistance) {
                continue;
            }

            int x = field.xOf(currentIndex);
            int y = field.yOf(currentIndex);
            int z = field.zOf(currentIndex);

            for (int[] offset : NEIGHBOURS) {
                int nextX = x + offset[0];
                int nextY = y + offset[1];
                int nextZ = z + offset[2];

                if (!field.contains(nextX, nextY, nextZ)
                        || !openForExposure(nextX, nextY, nextZ)) {
                    continue;
                }

                int nextIndex = field.indexOf(nextX, nextY, nextZ);
                if (field.visited(nextIndex)) {
                    continue;
                }

                field.setDistance(nextIndex, distance + 1);
                queue[tail++] = nextIndex;
            }
        }

        return field;
    }

    private boolean openForExposure(
            int x,
            int y,
            int z) {

        return !volume.solid(x, y, z)
                && !volume.opaque(x, y, z);
    }

    private TopOpaque findTopOpaque(
            int x,
            int y) {

        if (volume.empty()) {
            return TopOpaque.absent();
        }

        int minZ = volume.minOccupiedZ();
        int maxZ = volume.maxOccupiedZ();
        for (long lz = maxZ; lz >= minZ; lz--) {
            int z = (int) lz;
            if (volume.opaque(x, y, z)) {
                return new TopOpaque(true, z);
            }
        }

        return TopOpaque.absent();
    }

    private int terrainBodyDepth(
            int x,
            int y,
            int startZ) {

        if (view.terrainExtents().empty()
                || startZ > view.terrainExtents().maxZ()) {
            return 0;
        }

        int depth = 0;
        int maxZ = view.terrainExtents().maxZ();
        for (long lz = startZ; lz <= maxZ; lz++) {
            int z = (int) lz;
            if (!view.terrain().contains(x, y, z)) {
                break;
            }
            depth++;
        }
        return depth;
    }

    private Cover coverAt(
            int x,
            int y,
            int airZ) {

        if (volume.empty()) {
            return Cover.open();
        }

        int maxZ = volume.maxOccupiedZ();
        if (airZ >= maxZ) {
            return Cover.open();
        }

        int ceilingZ = Integer.MIN_VALUE;
        for (long lz = (long) airZ + 1L; lz <= maxZ; lz++) {
            int z = (int) lz;
            if (volume.opaque(x, y, z)) {
                ceilingZ = z;
                break;
            }
        }

        if (ceilingZ == Integer.MIN_VALUE) {
            return Cover.open();
        }

        int depth = 0;
        for (long lz = ceilingZ; lz <= maxZ; lz++) {
            int z = (int) lz;
            if (!volume.opaque(x, y, z)) {
                break;
            }
            depth++;
        }

        return new Cover(
                ceilingZ - airZ,
                depth);
    }

    private static int safeAdd(
            int value,
            int delta) {

        long result = (long) value + delta;
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) result;
    }

    private static final int[][] NEIGHBOURS = {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1}
    };

    private static final class ExposureField {

        private static final int UNVISITED = -1;

        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final int sizeX;
        private final int sizeY;
        private final int planeSize;
        private final int[] distances;

        private ExposureField(
                int minX,
                int maxX,
                int minY,
                int maxY,
                int minZ,
                int maxZ) {

            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;

            long width = (long) maxX - minX + 1L;
            long height = (long) maxY - minY + 1L;
            long depth = (long) maxZ - minZ + 1L;
            long plane = width * height;
            long volume = plane * depth;

            if (width <= 0L || height <= 0L || depth <= 0L
                    || width > Integer.MAX_VALUE
                    || height > Integer.MAX_VALUE
                    || plane > Integer.MAX_VALUE
                    || volume > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "exposure analysis volume is too large");
            }

            sizeX = (int) width;
            sizeY = (int) height;
            planeSize = (int) plane;
            distances = new int[(int) volume];
            Arrays.fill(distances, UNVISITED);
        }

        private int size() {
            return distances.length;
        }

        private boolean contains(
                int x,
                int y,
                int z) {

            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        private int indexOf(
                int x,
                int y,
                int z) {

            int dx = x - minX;
            int dy = y - minY;
            int dz = z - minZ;
            return dz * planeSize + dy * sizeX + dx;
        }

        private int xOf(
                int index) {

            return minX + index % planeSize % sizeX;
        }

        private int yOf(
                int index) {

            return minY + index % planeSize / sizeX;
        }

        private int zOf(
                int index) {

            return minZ + index / planeSize;
        }

        private boolean visited(
                int index) {

            return distances[index] != UNVISITED;
        }

        private int distanceAt(
                int index) {

            return distances[index];
        }

        private int distanceAt(
                int x,
                int y,
                int z,
                int fallback) {

            if (!contains(x, y, z)) {
                return fallback;
            }

            int distance = distances[indexOf(x, y, z)];
            return distance == UNVISITED ? fallback : distance;
        }

        private void setDistance(
                int index,
                int distance) {

            distances[index] = distance;
        }
    }

    private record TopOpaque(boolean present, int z) {
        static TopOpaque absent() {
            return new TopOpaque(false, 0);
        }
    }

    private record Cover(int ceilingDistance, int depth) {
        static Cover open() {
            return new Cover(0, 0);
        }
    }
}
