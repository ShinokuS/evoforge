package io.github.evoforge.visualizer.visual;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

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
        private final Map<Position, Integer> exposureDistances;

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
            exposureDistances = buildExposureField(
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
                int bodyDepth = terrainBodyDepth(
                        x,
                        y,
                        selectedStandingZ);
                return new Cell(
                        Kind.SOLID_BODY,
                        selectedStandingZ,
                        0,
                        bodyDepth,
                        0,
                        0,
                        0,
                        view.geometry().find(x, y, selectedStandingZ));
            }

            int supportTerrainZ = selectedStandingZ - 1;
            if (view.terrain().contains(x, y, supportTerrainZ)) {
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
            int exposureDistance = exposureDistances.getOrDefault(
                    new Position(x, y, airZ),
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

    private Map<Position, Integer> buildExposureField(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ,
            int maxLowerDepth,
            int maxExposureDistance) {

        Map<Position, Integer> distances = new HashMap<>();

        int expandedMinX = safeAdd(minX, -maxExposureDistance);
        int expandedMaxX = safeAdd(maxX, maxExposureDistance);
        int expandedMinY = safeAdd(minY, -maxExposureDistance);
        int expandedMaxY = safeAdd(maxY, maxExposureDistance);
        int verticalMargin = maxLowerDepth + maxExposureDistance + 1;
        int minZ = safeAdd(selectedStandingZ, -verticalMargin);
        int maxZ = safeAdd(selectedStandingZ, maxExposureDistance + 1);

        ArrayDeque<Position> queue = new ArrayDeque<>();
        Map<Column, TopOpaque> topOpaque = new HashMap<>();

        for (long lx = expandedMinX; lx <= expandedMaxX; lx++) {
            int x = (int) lx;
            for (long ly = expandedMinY; ly <= expandedMaxY; ly++) {
                int y = (int) ly;
                TopOpaque top = findTopOpaque(x, y);
                topOpaque.put(new Column(x, y), top);

                for (long lz = minZ; lz <= maxZ; lz++) {
                    int z = (int) lz;
                    if (!openForExposure(x, y, z)) {
                        continue;
                    }
                    if (top.present() && z <= top.z()) {
                        continue;
                    }

                    Position position = new Position(x, y, z);
                    distances.put(position, 0);
                    queue.addLast(position);
                }
            }
        }

        while (!queue.isEmpty()) {
            Position current = queue.removeFirst();
            int distance = distances.get(current);
            if (distance >= maxExposureDistance) {
                continue;
            }

            for (int[] offset : NEIGHBOURS) {
                int x = current.x() + offset[0];
                int y = current.y() + offset[1];
                int z = current.z() + offset[2];

                if (x < expandedMinX || x > expandedMaxX
                        || y < expandedMinY || y > expandedMaxY
                        || z < minZ || z > maxZ
                        || !openForExposure(x, y, z)) {
                    continue;
                }

                Position next = new Position(x, y, z);
                if (distances.containsKey(next)) {
                    continue;
                }

                distances.put(next, distance + 1);
                queue.addLast(next);
            }
        }

        return distances;
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

        int depth = 0;
        for (long lz = startZ; lz <= Integer.MAX_VALUE; lz++) {
            int z = (int) lz;
            if (!view.terrain().contains(x, y, z)) {
                break;
            }
            depth++;
            if (z == Integer.MAX_VALUE) {
                break;
            }
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

    private record Position(int x, int y, int z) {
    }

    private record Column(int x, int y) {
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
