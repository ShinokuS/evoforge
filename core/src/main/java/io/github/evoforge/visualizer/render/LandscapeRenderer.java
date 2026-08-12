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
    private static final int MIN_ANALYSIS_PADDING = 8;
    private static final int ANALYSIS_PADDING_DIVISOR = 4;
    private static final int ANALYSIS_CACHE_SIZE = 6;
    private static final long PERF_LOG_INTERVAL_NANOS = 1_000_000_000L;

    private final SimulationView view;
    private final ProceduralLandscapePack surfaceArt;
    private final ProceduralSliceArt sliceArt;
    private final LandscapeSliceResolver sliceResolver;
    private final AnalysisCacheEntry[] analysisCache =
            new AnalysisCacheEntry[ANALYSIS_CACHE_SIZE];

    private long cacheUseSequence;
    private long perfWindowStartNanos = System.nanoTime();
    private long perfAnalysisNanos;
    private long perfLandscapeNanos;
    private long perfMaxAnalysisNanos;
    private long perfMaxLandscapeNanos;
    private int perfFrames;
    private int perfCacheHits;
    private int perfCacheMisses;
    private long lastVisibleCells;
    private int lastAnalysisPadding;

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
        if (visibilityRevision >= 0L) {
            for (AnalysisCacheEntry entry : analysisCache) {
                if (entry != null
                        && entry.matches(
                                visibilityRevision,
                                selectedStandingZ,
                                maxLowerDepth,
                                minX,
                                maxX,
                                minY,
                                maxY)) {
                    perfCacheHits++;
                    entry.lastUse = ++cacheUseSequence;
                    lastAnalysisPadding = entry.padding;
                    return entry.analysis;
                }
            }
        }

        perfCacheMisses++;
        int padding = analysisPadding(minX, maxX, minY, maxY);
        int analysisMinX = safeAdd(minX, -padding);
        int analysisMaxX = safeAdd(maxX, padding);
        int analysisMinY = safeAdd(minY, -padding);
        int analysisMaxY = safeAdd(maxY, padding);
        LandscapeSliceResolver.Analysis analysis = sliceResolver.analyze(
                analysisMinX,
                analysisMaxX,
                analysisMinY,
                analysisMaxY,
                selectedStandingZ,
                maxLowerDepth,
                EXPOSURE_DISTANCE);

        lastAnalysisPadding = padding;
        if (visibilityRevision >= 0L) {
            int slot = replacementSlot();
            analysisCache[slot] = new AnalysisCacheEntry(
                    analysis,
                    visibilityRevision,
                    selectedStandingZ,
                    maxLowerDepth,
                    analysisMinX,
                    analysisMaxX,
                    analysisMinY,
                    analysisMaxY,
                    padding,
                    ++cacheUseSequence);
        }
        return analysis;
    }

    private static int analysisPadding(
            int minX,
            int maxX,
            int minY,
            int maxY) {

        long width = (long) maxX - minX + 1L;
        long height = (long) maxY - minY + 1L;
        long viewportSpan = Math.max(width, height);
        long scaled = (viewportSpan + ANALYSIS_PADDING_DIVISOR - 1L)
                / ANALYSIS_PADDING_DIVISOR;
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(MIN_ANALYSIS_PADDING, scaled));
    }

    private int replacementSlot() {
        int oldestSlot = 0;
        long oldestUse = Long.MAX_VALUE;

        for (int index = 0; index < analysisCache.length; index++) {
            AnalysisCacheEntry entry = analysisCache[index];
            if (entry == null) {
                return index;
            }
            if (entry.lastUse < oldestUse) {
                oldestUse = entry.lastUse;
                oldestSlot = index;
            }
        }
        return oldestSlot;
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
                        + perfCacheHits + "/" + perfCacheMisses
                        + " padding=" + lastAnalysisPadding
                        + " cached=" + cachedEntryCount());

        perfWindowStartNanos = now;
        perfAnalysisNanos = 0L;
        perfLandscapeNanos = 0L;
        perfMaxAnalysisNanos = 0L;
        perfMaxLandscapeNanos = 0L;
        perfFrames = 0;
        perfCacheHits = 0;
        perfCacheMisses = 0;
    }

    private int cachedEntryCount() {
        int count = 0;
        for (AnalysisCacheEntry entry : analysisCache) {
            if (entry != null) {
                count++;
            }
        }
        return count;
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

    private static final class AnalysisCacheEntry {

        private final LandscapeSliceResolver.Analysis analysis;
        private final long visibilityRevision;
        private final int standingZ;
        private final int lowerDepth;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int padding;
        private long lastUse;

        private AnalysisCacheEntry(
                LandscapeSliceResolver.Analysis analysis,
                long visibilityRevision,
                int standingZ,
                int lowerDepth,
                int minX,
                int maxX,
                int minY,
                int maxY,
                int padding,
                long lastUse) {

            this.analysis = analysis;
            this.visibilityRevision = visibilityRevision;
            this.standingZ = standingZ;
            this.lowerDepth = lowerDepth;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.padding = padding;
            this.lastUse = lastUse;
        }

        private boolean matches(
                long revision,
                int selectedStandingZ,
                int maxLowerDepth,
                int requestedMinX,
                int requestedMaxX,
                int requestedMinY,
                int requestedMaxY) {

            return visibilityRevision == revision
                    && standingZ == selectedStandingZ
                    && lowerDepth == maxLowerDepth
                    && requestedMinX >= minX
                    && requestedMaxX <= maxX
                    && requestedMinY >= minY
                    && requestedMaxY <= maxY;
        }
    }
}
