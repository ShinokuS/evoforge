package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;
import io.github.evoforge.visualizer.visual.LandscapeTopology;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;
import io.github.evoforge.visualizer.visual.ProceduralSliceArt;

/** Draws authoritative landscape using geometry-derived cutaway visibility. */
public final class LandscapeRenderer {

    private static final int EXPOSURE_DISTANCE = 12;
    private static final int ANALYSIS_PADDING = 8;
    private static final long PERF_LOG_INTERVAL_NANOS = 1_000_000_000L;

    private final SimulationView view;
    private final ProceduralLandscapePack surfaceArt;
    private final ProceduralSliceArt sliceArt;
    private final LandscapeSliceResolver sliceResolver;

    private LandscapeSliceResolver.Analysis cachedAnalysis;
    private long cachedVisibilityRevision = Long.MIN_VALUE;
    private int cachedMinX;
    private int cachedMaxX;
    private int cachedMinY;
    private int cachedMaxY;
    private int cachedStandingZ;
    private int cachedLowerDepth;

    private long perfWindowStartNanos = System.nanoTime();
    private long perfAnalysisNanos;
    private long perfLandscapeNanos;
    private long perfMaxAnalysisNanos;
    private long perfMaxLandscapeNanos;
    private int perfFrames;
    private int perfCacheHits;
    private int perfCacheMisses;
    private long lastVisibleCells;

    public LandscapeRenderer(
            SimulationView view,
            ProceduralLandscapePack surfaceArt,
            ProceduralSliceArt sliceArt,
            LandscapeSliceResolver sliceResolver) {

        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (surfaceArt == null) {
            throw new IllegalArgumentException("surfaceArt must not be null");
        }
        if (sliceArt == null) {
            throw new IllegalArgumentException("sliceArt must not be null");
        }
        if (sliceResolver == null) {
            throw new IllegalArgumentException("sliceResolver must not be null");
        }

        this.view = view;
        this.surfaceArt = surfaceArt;
        this.sliceArt = sliceArt;
        this.sliceResolver = sliceResolver;
    }

    public void draw(
            SpriteBatch batch,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ,
            int maxLowerDepth) {

        long landscapeStart = System.nanoTime();
        long analysisStart = landscapeStart;
        LandscapeSliceResolver.Analysis analysis = analysisFor(
                minX,
                maxX,
                minY,
                maxY,
                selectedStandingZ,
                maxLowerDepth);
        long analysisNanos = System.nanoTime() - analysisStart;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                drawCell(batch, analysis, x, y);
            }
        }

        batch.setColor(Color.WHITE);

        long landscapeNanos = System.nanoTime() - landscapeStart;
        lastVisibleCells = (long) (maxX - minX + 1)
                * (long) (maxY - minY + 1);
        recordPerformance(analysisNanos, landscapeNanos);
    }

    private LandscapeSliceResolver.Analysis analysisFor(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ,
            int maxLowerDepth) {

        long visibilityRevision = sliceResolver.visibilityRevision();
        boolean cacheHit = visibilityRevision >= 0L
                && cachedAnalysis != null
                && cachedVisibilityRevision == visibilityRevision
                && cachedStandingZ == selectedStandingZ
                && cachedLowerDepth == maxLowerDepth
                && minX >= cachedMinX
                && maxX <= cachedMaxX
                && minY >= cachedMinY
                && maxY <= cachedMaxY;

        if (cacheHit) {
            perfCacheHits++;
            return cachedAnalysis;
        }

        perfCacheMisses++;
        cachedVisibilityRevision = visibilityRevision;
        cachedStandingZ = selectedStandingZ;
        cachedLowerDepth = maxLowerDepth;
        cachedMinX = safeAdd(minX, -ANALYSIS_PADDING);
        cachedMaxX = safeAdd(maxX, ANALYSIS_PADDING);
        cachedMinY = safeAdd(minY, -ANALYSIS_PADDING);
        cachedMaxY = safeAdd(maxY, ANALYSIS_PADDING);
        cachedAnalysis = sliceResolver.analyze(
                cachedMinX,
                cachedMaxX,
                cachedMinY,
                cachedMaxY,
                selectedStandingZ,
                maxLowerDepth,
                EXPOSURE_DISTANCE);
        return cachedAnalysis;
    }

    private void recordPerformance(
            long analysisNanos,
            long landscapeNanos) {

        perfFrames++;
        perfAnalysisNanos += analysisNanos;
        perfLandscapeNanos += landscapeNanos;
        perfMaxAnalysisNanos = Math.max(perfMaxAnalysisNanos, analysisNanos);
        perfMaxLandscapeNanos = Math.max(perfMaxLandscapeNanos, landscapeNanos);

        long now = System.nanoTime();
        if (now - perfWindowStartNanos < PERF_LOG_INTERVAL_NANOS) {
            return;
        }

        long frames = Math.max(1, perfFrames);
        Gdx.app.log(
                "VisualizerPerf",
                "fps=" + Gdx.graphics.getFramesPerSecond()
                        + " visible=" + lastVisibleCells
                        + " landscape avg/max="
                        + millis(perfLandscapeNanos / frames)
                        + "/" + millis(perfMaxLandscapeNanos) + "ms"
                        + " analysis avg/max="
                        + millis(perfAnalysisNanos / frames)
                        + "/" + millis(perfMaxAnalysisNanos) + "ms"
                        + " cache hit/miss="
                        + perfCacheHits + "/" + perfCacheMisses);

        perfWindowStartNanos = now;
        perfAnalysisNanos = 0L;
        perfLandscapeNanos = 0L;
        perfMaxAnalysisNanos = 0L;
        perfMaxLandscapeNanos = 0L;
        perfFrames = 0;
        perfCacheHits = 0;
        perfCacheMisses = 0;
    }

    private static double millis(
            long nanos) {

        return Math.round(nanos / 10_000.0) / 100.0;
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

    private void drawCell(
            SpriteBatch batch,
            LandscapeSliceResolver.Analysis analysis,
            int x,
            int y) {

        LandscapeSliceResolver.Cell cell = analysis.resolve(x, y);
        if (cell.kind() == LandscapeSliceResolver.Kind.EMPTY) {
            return;
        }

        int variant = LandscapeTopology.variant(
                x,
                y,
                cell.terrainZ(),
                ProceduralLandscapePack.SURFACE_VARIANTS);
        int topology = neighbourMask(x, y, cell.terrainZ());

        TextureRegion region;
        if (cell.kind() == LandscapeSliceResolver.Kind.SOLID_BODY) {
            region = cell.shape() instanceof RampShape ramp
                    ? surfaceArt.ramp(ramp, variant)
                    : sliceArt.solid(topology, variant);
            setSolidTint(batch, cell.bodyDepth());
        } else {
            region = cell.shape() instanceof RampShape ramp
                    ? surfaceArt.ramp(ramp, variant)
                    : surfaceArt.surface(topology, variant);
            setSurfaceTint(batch, cell);
        }

        batch.draw(region, x, y, 1f, 1f);
    }

    private static void setSolidTint(
            SpriteBatch batch,
            int bodyDepth) {

        float shade = switch (Math.min(bodyDepth, 7)) {
            case 1 -> 1.00f;
            case 2 -> 0.84f;
            case 3 -> 0.67f;
            case 4 -> 0.50f;
            case 5 -> 0.34f;
            case 6 -> 0.22f;
            default -> 0.14f;
        };
        batch.setColor(shade, shade, shade, 1f);
    }

    private static void setSurfaceTint(
            SpriteBatch batch,
            LandscapeSliceResolver.Cell cell) {

        float environment = environmentShade(cell);
        float drop = cell.kind() == LandscapeSliceResolver.Kind.LOWER_SURFACE
                ? dropShade(cell.dropDepth())
                : 1f;
        float shade = environment * drop;
        batch.setColor(shade, shade, shade, 1f);
    }

    private static float environmentShade(
            LandscapeSliceResolver.Cell cell) {

        if (!cell.covered()) {
            return 1f;
        }

        float cover = switch (Math.min(cell.coverDepth(), 7)) {
            case 1 -> 0.88f;
            case 2 -> 0.75f;
            case 3 -> 0.61f;
            case 4 -> 0.48f;
            case 5 -> 0.36f;
            case 6 -> 0.27f;
            default -> 0.20f;
        };
        float tallCavernRelief = Math.min(
                0.10f,
                Math.max(0, cell.ceilingDistance() - 1) * 0.02f);
        float exposure = Math.max(
                0.38f,
                1f - Math.min(cell.exposureDistance(), EXPOSURE_DISTANCE + 1)
                        * 0.048f);

        return Math.max(
                0.10f,
                Math.min(0.94f, cover + tallCavernRelief) * exposure);
    }

    private static float dropShade(
            int depth) {

        if (depth <= 0) {
            return 1f;
        }

        return switch (Math.min(depth, 8)) {
            case 1 -> 0.84f;
            case 2 -> 0.64f;
            case 3 -> 0.42f;
            case 4 -> 0.22f;
            case 5 -> 0.14f;
            case 6 -> 0.09f;
            case 7 -> 0.06f;
            default -> 0.045f;
        };
    }

    private int neighbourMask(
            int x,
            int y,
            int terrainZ) {

        int mask = 0;

        if (view.terrain().contains(x, y + 1, terrainZ)) {
            mask |= LandscapeTopology.N;
        }
        if (view.terrain().contains(x + 1, y + 1, terrainZ)) {
            mask |= LandscapeTopology.NE;
        }
        if (view.terrain().contains(x + 1, y, terrainZ)) {
            mask |= LandscapeTopology.E;
        }
        if (view.terrain().contains(x + 1, y - 1, terrainZ)) {
            mask |= LandscapeTopology.SE;
        }
        if (view.terrain().contains(x, y - 1, terrainZ)) {
            mask |= LandscapeTopology.S;
        }
        if (view.terrain().contains(x - 1, y - 1, terrainZ)) {
            mask |= LandscapeTopology.SW;
        }
        if (view.terrain().contains(x - 1, y, terrainZ)) {
            mask |= LandscapeTopology.W;
        }
        if (view.terrain().contains(x - 1, y + 1, terrainZ)) {
            mask |= LandscapeTopology.NW;
        }

        return LandscapeTopology.normalize(mask);
    }
}
