package io.github.evoforge.simulation.world.continuum.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Pure viewport planner for Stage 4.
 *
 * <p>The camera chooses only which derived representation to request. It never changes world truth.
 * Visible tiles are requested before speculative work. Prefetch is bounded by the tile service job
 * budget and biased toward the user's most recent camera action.</p>
 */
public final class ContinuumMapViewport {
    private static final double TARGET_TILE_PIXELS = 192d;
    private static final double LOD_KEEP_MIN_TILE_PIXELS = 132d;
    private static final double LOD_KEEP_MAX_TILE_PIXELS = 288d;
    private static final double MIN_PIXELS_PER_WORLD_UNIT = 1e-9d;
    private static final double MAX_PIXELS_PER_WORLD_UNIT = 32d;
    private static final int MAX_FINER_PREFETCH_TILES = 128;
    private static final int MAX_COARSER_PREFETCH_TILES = 32;

    private final long worldWidth;
    private final long worldHeight;
    private final int tileSampleSide;
    private final int maxLevel;
    private final int prefetchRing;

    private double centerX;
    private double centerY;
    private double pixelsPerWorldUnit;
    private int viewportWidthPixels;
    private int viewportHeightPixels;
    private int selectedLevel;
    private long sourceRevision;
    private MotionHint motionHint = MotionHint.NONE;

    public ContinuumMapViewport(
            long worldWidth,
            long worldHeight,
            int tileSampleSide,
            int maxLevel,
            int prefetchRing,
            int viewportWidthPixels,
            int viewportHeightPixels) {
        if (worldWidth <= 0L || worldHeight <= 0L) throw new IllegalArgumentException("world dimensions must be > 0");
        if (tileSampleSide <= 0) throw new IllegalArgumentException("tileSampleSide must be > 0");
        if (maxLevel < 0 || maxLevel >= Long.SIZE - 1) throw new IllegalArgumentException("maxLevel outside supported range");
        if (prefetchRing < 0) throw new IllegalArgumentException("prefetchRing must be >= 0");
        if (viewportWidthPixels <= 0 || viewportHeightPixels <= 0) throw new IllegalArgumentException("viewport must be > 0");
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.tileSampleSide = tileSampleSide;
        this.maxLevel = maxLevel;
        this.prefetchRing = prefetchRing;
        this.viewportWidthPixels = viewportWidthPixels;
        this.viewportHeightPixels = viewportHeightPixels;
        this.centerX = (worldWidth - 1d) * 0.5d;
        this.centerY = (worldHeight - 1d) * 0.5d;
        fitWholeWorld();
    }

    public void resize(int widthPixels, int heightPixels) {
        if (widthPixels <= 0 || heightPixels <= 0) return;
        viewportWidthPixels = widthPixels;
        viewportHeightPixels = heightPixels;
        updateSelectedLevel();
    }

    public void setSourceRevision(long revision) {
        sourceRevision = revision;
    }

    public long sourceRevision() {
        return sourceRevision;
    }

    public double centerX() {
        return centerX;
    }

    public double centerY() {
        return centerY;
    }

    public double pixelsPerWorldUnit() {
        return pixelsPerWorldUnit;
    }

    public int desiredLevel() {
        return selectedLevel;
    }

    public long tileWorldSpan(int level) {
        if (level < 0 || level > maxLevel) throw new IllegalArgumentException("level outside viewport hierarchy");
        return Math.multiplyExact((long) tileSampleSide, 1L << level);
    }

    public void fitWholeWorld() {
        double fitX = viewportWidthPixels / (double) worldWidth;
        double fitY = viewportHeightPixels / (double) worldHeight;
        pixelsPerWorldUnit = clamp(Math.min(fitX, fitY) * 0.94d, MIN_PIXELS_PER_WORLD_UNIT, MAX_PIXELS_PER_WORLD_UNIT);
        centerX = (worldWidth - 1d) * 0.5d;
        centerY = (worldHeight - 1d) * 0.5d;
        selectedLevel = idealLevel();
        motionHint = MotionHint.NONE;
    }

    /** Moves the camera by a screen-space drag delta. */
    public void panPixels(double deltaScreenX, double deltaScreenY) {
        centerX -= deltaScreenX / pixelsPerWorldUnit;
        centerY += deltaScreenY / pixelsPerWorldUnit;
        clampCenter();
        if (deltaScreenX != 0d || deltaScreenY != 0d) motionHint = MotionHint.PAN;
    }

    /** Zooms around a screen position, preserving the world coordinate under the cursor. */
    public void zoomAt(double factor, double screenX, double screenY) {
        if (!(factor > 0d) || !Double.isFinite(factor)) throw new IllegalArgumentException("factor must be finite and > 0");
        double anchorWorldX = worldXAtScreen(screenX);
        double anchorWorldY = worldYAtScreen(screenY);
        pixelsPerWorldUnit = clamp(pixelsPerWorldUnit * factor, minimumUsefulZoom(), MAX_PIXELS_PER_WORLD_UNIT);
        centerX = anchorWorldX - (screenX - viewportWidthPixels * 0.5d) / pixelsPerWorldUnit;
        centerY = anchorWorldY - (viewportHeightPixels * 0.5d - screenY) / pixelsPerWorldUnit;
        clampCenter();
        updateSelectedLevel();
        motionHint = factor > 1d ? MotionHint.ZOOM_IN : MotionHint.ZOOM_OUT;
    }

    public double worldXAtScreen(double screenX) {
        return centerX + (screenX - viewportWidthPixels * 0.5d) / pixelsPerWorldUnit;
    }

    public double worldYAtScreen(double screenYFromTop) {
        return centerY + (viewportHeightPixels * 0.5d - screenYFromTop) / pixelsPerWorldUnit;
    }

    public double screenXForWorld(double worldX) {
        return viewportWidthPixels * 0.5d + (worldX - centerX) * pixelsPerWorldUnit;
    }

    public double screenYForWorld(double worldY) {
        return viewportHeightPixels * 0.5d + (worldY - centerY) * pixelsPerWorldUnit;
    }

    public Frame requestFrame(ContinuumMapTileService service) {
        if (service == null) throw new IllegalArgumentException("service must not be null");
        if (service.activeRevision() != sourceRevision) service.setRevision(sourceRevision);

        int level = desiredLevel();
        long span = tileWorldSpan(level);
        TileRange visible = visibleRange(span, 0);
        TileRange prefetched = visibleRange(span, prefetchRing);

        List<ContinuumMapTileKey> visibleKeys = orderedKeys(visible, level, Integer.MAX_VALUE);
        List<ContinuumMapTileKey> spatialCandidates = orderedKeys(prefetched, level, Integer.MAX_VALUE);
        spatialCandidates.removeAll(visibleKeys);

        List<ContinuumMapTileKey> finerCandidates = level > 0
                ? orderedKeys(visibleRange(tileWorldSpan(level - 1), 0), level - 1, MAX_FINER_PREFETCH_TILES)
                : List.of();
        List<ContinuumMapTileKey> coarserCandidates = level < maxLevel
                ? orderedKeys(visibleRange(tileWorldSpan(level + 1), 0), level + 1, MAX_COARSER_PREFETCH_TILES)
                : List.of();

        LinkedHashSet<ContinuumMapTileKey> orderedSpeculativeCandidates = new LinkedHashSet<>();
        switch (motionHint) {
            case ZOOM_IN -> {
                orderedSpeculativeCandidates.addAll(finerCandidates);
                orderedSpeculativeCandidates.addAll(spatialCandidates);
                orderedSpeculativeCandidates.addAll(coarserCandidates);
            }
            case ZOOM_OUT -> {
                orderedSpeculativeCandidates.addAll(coarserCandidates);
                orderedSpeculativeCandidates.addAll(spatialCandidates);
                orderedSpeculativeCandidates.addAll(finerCandidates);
            }
            case PAN -> {
                orderedSpeculativeCandidates.addAll(spatialCandidates);
                orderedSpeculativeCandidates.addAll(finerCandidates);
                orderedSpeculativeCandidates.addAll(coarserCandidates);
            }
            case NONE -> {
                orderedSpeculativeCandidates.addAll(finerCandidates);
                orderedSpeculativeCandidates.addAll(spatialCandidates);
                orderedSpeculativeCandidates.addAll(coarserCandidates);
            }
        }
        orderedSpeculativeCandidates.removeAll(visibleKeys);

        int speculativeBudget = Math.max(0, service.maxOutstandingJobs() - visibleKeys.size());
        LinkedHashSet<ContinuumMapTileKey> speculative = takeFirst(orderedSpeculativeCandidates, speculativeBudget);

        LinkedHashSet<ContinuumMapTileKey> demanded = new LinkedHashSet<>(visibleKeys);
        demanded.addAll(speculative);
        service.retainPendingDemand(demanded);

        for (ContinuumMapTileKey key : visibleKeys) service.requestVisible(key);
        for (ContinuumMapTileKey key : speculative) service.requestPrefetch(key);

        List<DisplayTile> display = new ArrayList<>(visibleKeys.size());
        int fallbackCount = 0;
        int exactReadyCount = 0;
        for (ContinuumMapTileKey target : visibleKeys) {
            Optional<ContinuumMapTile> available = service.bestAvailable(target);
            if (available.isEmpty()) continue;
            ContinuumMapTile source = available.get();
            int fallbackDepth = source.key().level() - target.level();
            if (fallbackDepth == 0) exactReadyCount++;
            else fallbackCount++;
            display.add(new DisplayTile(target, source, fallbackDepth));
        }

        return new Frame(
                level,
                List.copyOf(display),
                visibleKeys.size(),
                demanded.size(),
                exactReadyCount,
                fallbackCount);
    }

    private List<ContinuumMapTileKey> orderedKeys(TileRange range, int level, int limit) {
        List<ContinuumMapTileKey> keys = new ArrayList<>(range.count());
        for (long tileY = range.minY(); tileY <= range.maxY(); tileY++) {
            for (long tileX = range.minX(); tileX <= range.maxX(); tileX++) {
                keys.add(new ContinuumMapTileKey(level, tileX, tileY, sourceRevision));
            }
        }

        long span = tileWorldSpan(level);
        double centerTileX = centerX / span;
        double centerTileY = centerY / span;
        keys.sort(Comparator.comparingDouble(key -> {
            double dx = key.tileX() + 0.5d - centerTileX;
            double dy = key.tileY() + 0.5d - centerTileY;
            return dx * dx + dy * dy;
        }));

        if (keys.size() <= limit) return keys;
        return new ArrayList<>(keys.subList(0, limit));
    }

    private static LinkedHashSet<ContinuumMapTileKey> takeFirst(
            LinkedHashSet<ContinuumMapTileKey> candidates,
            int limit) {
        LinkedHashSet<ContinuumMapTileKey> result = new LinkedHashSet<>(Math.min(candidates.size(), limit));
        if (limit <= 0) return result;
        for (ContinuumMapTileKey key : candidates) {
            result.add(key);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private TileRange visibleRange(long span, int ring) {
        double halfWorldWidth = viewportWidthPixels / (2d * pixelsPerWorldUnit);
        double halfWorldHeight = viewportHeightPixels / (2d * pixelsPerWorldUnit);
        double left = Math.max(0d, centerX - halfWorldWidth);
        double right = Math.min(worldWidth - 1d, centerX + halfWorldWidth);
        double bottom = Math.max(0d, centerY - halfWorldHeight);
        double top = Math.min(worldHeight - 1d, centerY + halfWorldHeight);

        long maxTileX = ceilDiv(worldWidth, span) - 1L;
        long maxTileY = ceilDiv(worldHeight, span) - 1L;
        long minX = clamp((long) Math.floor(left / span) - ring, 0L, maxTileX);
        long maxX = clamp((long) Math.floor(right / span) + ring, 0L, maxTileX);
        long minY = clamp((long) Math.floor(bottom / span) - ring, 0L, maxTileY);
        long maxY = clamp((long) Math.floor(top / span) + ring, 0L, maxTileY);
        return new TileRange(minX, maxX, minY, maxY);
    }

    private int idealLevel() {
        double baseTilePixels = tileSampleSide * pixelsPerWorldUnit;
        if (baseTilePixels <= 0d) return maxLevel;
        double level = Math.log(TARGET_TILE_PIXELS / baseTilePixels) / Math.log(2d);
        return clamp((int) Math.round(level), 0, maxLevel);
    }

    private void updateSelectedLevel() {
        selectedLevel = clamp(selectedLevel, 0, maxLevel);
        while (selectedLevel < maxLevel && tilePixels(selectedLevel) < LOD_KEEP_MIN_TILE_PIXELS) {
            selectedLevel++;
        }
        while (selectedLevel > 0 && tilePixels(selectedLevel) > LOD_KEEP_MAX_TILE_PIXELS) {
            selectedLevel--;
        }
    }

    private double tilePixels(int level) {
        return Math.scalb(tileSampleSide * pixelsPerWorldUnit, level);
    }

    private void clampCenter() {
        centerX = clamp(centerX, 0d, worldWidth - 1d);
        centerY = clamp(centerY, 0d, worldHeight - 1d);
    }

    private double minimumUsefulZoom() {
        double fitX = viewportWidthPixels / (double) worldWidth;
        double fitY = viewportHeightPixels / (double) worldHeight;
        return clamp(Math.min(fitX, fitY) * 0.25d, MIN_PIXELS_PER_WORLD_UNIT, MAX_PIXELS_PER_WORLD_UNIT);
    }

    private static long ceilDiv(long value, long divisor) {
        return 1L + (value - 1L) / divisor;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(value, max));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private enum MotionHint {
        NONE,
        PAN,
        ZOOM_IN,
        ZOOM_OUT
    }

    private record TileRange(long minX, long maxX, long minY, long maxY) {
        int count() {
            return Math.toIntExact(Math.multiplyExact(maxX - minX + 1L, maxY - minY + 1L));
        }
    }

    public record Frame(
            int desiredLevel,
            List<DisplayTile> tiles,
            int visibleTileCount,
            int requestedWithPrefetch,
            int exactReadyCount,
            int fallbackCount) {}

    public record DisplayTile(
            ContinuumMapTileKey targetKey,
            ContinuumMapTile sourceTile,
            int fallbackDepth) {
        public DisplayTile {
            if (targetKey == null || sourceTile == null) throw new IllegalArgumentException("tiles must not be null");
            if (fallbackDepth < 0) throw new IllegalArgumentException("fallbackDepth must be >= 0");
        }

        public float u0() {
            return coordinateFraction(targetKey.tileX());
        }

        public float v0() {
            return coordinateFraction(targetKey.tileY());
        }

        public float u1() {
            return u0() + fractionSize();
        }

        public float v1() {
            return v0() + fractionSize();
        }

        private float fractionSize() {
            return 1f / (1 << Math.min(fallbackDepth, 30));
        }

        private float coordinateFraction(long targetCoordinate) {
            if (fallbackDepth == 0) return 0f;
            long scale = 1L << Math.min(fallbackDepth, 62);
            long offset = Math.floorMod(targetCoordinate, scale);
            return (float) (offset / (double) scale);
        }
    }
}
