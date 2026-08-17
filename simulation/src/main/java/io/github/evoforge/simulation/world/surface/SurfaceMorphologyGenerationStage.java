package io.github.evoforge.simulation.world.surface;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Deterministically derives reusable local topographic facts from generated elevation. */
public final class SurfaceMorphologyGenerationStage implements SurfaceMorphologyGenerator {

    @Override
    public SurfaceMorphologyField generate(ElevationField elevation) {
        if (elevation == null) {
            throw new IllegalArgumentException("elevation must not be null");
        }
        WorldBounds bounds = elevation.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));
        long[] maximumSlope = new long[area];
        long[] convexity = new long[area];
        long[] concavity = new long[area];

        int index = 0;
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                LocalMorphology morphology = morphologyAt(elevation, worldX, worldY);
                maximumSlope[index] = morphology.maximumSlope();
                convexity[index] = morphology.convexity();
                concavity[index] = morphology.concavity();
                index++;
            }
        }
        return new DenseSurfaceMorphologyField(
                bounds,
                width,
                maximumSlope,
                convexity,
                concavity);
    }

    private static LocalMorphology morphologyAt(ElevationField elevation, int x, int y) {
        WorldBounds bounds = elevation.bounds();
        long center = elevation.elevationSubunitsAt(x, y);
        long maximumSlope = 0L;
        long neighborSum = 0L;
        int neighbors = 0;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                long nx = (long) x + dx;
                long ny = (long) y + dy;
                if (nx < bounds.minX() || nx > bounds.maxX()
                        || ny < bounds.minY() || ny > bounds.maxY()) {
                    continue;
                }
                long neighbor = elevation.elevationSubunitsAt((int) nx, (int) ny);
                maximumSlope = Math.max(maximumSlope, Math.abs(neighbor - center));
                neighborSum = Math.addExact(neighborSum, neighbor);
                neighbors++;
            }
        }

        long convexity = 0L;
        long concavity = 0L;
        if (neighbors > 0) {
            long averageNeighbor = neighborSum / neighbors;
            convexity = Math.max(0L, center - averageNeighbor);
            concavity = Math.max(0L, averageNeighbor - center);
        }
        return new LocalMorphology(maximumSlope, convexity, concavity);
    }

    private record LocalMorphology(
            long maximumSlope,
            long convexity,
            long concavity) { }

    private static final class DenseSurfaceMorphologyField implements SurfaceMorphologyField {
        private final WorldBounds bounds;
        private final int width;
        private final long[] maximumSlope;
        private final long[] convexity;
        private final long[] concavity;

        private DenseSurfaceMorphologyField(
                WorldBounds bounds,
                int width,
                long[] maximumSlope,
                long[] convexity,
                long[] concavity) {
            this.bounds = bounds;
            this.width = width;
            this.maximumSlope = maximumSlope;
            this.convexity = convexity;
            this.concavity = concavity;
        }

        @Override
        public WorldBounds bounds() {
            return bounds;
        }

        @Override
        public long maximumNeighborSlopeSubunitsAt(int x, int y) {
            return maximumSlope[indexOf(x, y)];
        }

        @Override
        public long convexitySubunitsAt(int x, int y) {
            return convexity[indexOf(x, y)];
        }

        @Override
        public long concavitySubunitsAt(int x, int y) {
            return concavity[indexOf(x, y)];
        }

        private int indexOf(int x, int y) {
            if (x < bounds.minX() || x > bounds.maxX()
                    || y < bounds.minY() || y > bounds.maxY()) {
                throw new IllegalArgumentException(
                        "surface morphology coordinate outside world bounds: ("
                                + x + ", " + y + ")");
            }
            return (y - bounds.minY()) * width + (x - bounds.minX());
        }
    }
}
