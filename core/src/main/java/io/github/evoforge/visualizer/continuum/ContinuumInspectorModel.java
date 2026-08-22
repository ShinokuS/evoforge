package io.github.evoforge.visualizer.continuum;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumRandom;
import io.github.evoforge.simulation.world.continuum.model.ContinuumResolution;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageCacheMetrics;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.continuum.page.ContinuumScalarPageCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Presentation-side state for Continuum page/cache and multi-resolution inspection.
 *
 * <p>Moving the focus or changing sampling resolution changes only requested representation. It never
 * changes the coordinate-addressed field or Continuum world truth.</p>
 */
public final class ContinuumInspectorModel {
    public static final long DEFAULT_LOGICAL_SIDE = 1_000_000L;
    public static final int DEFAULT_PAGE_SIDE = 256;
    public static final int DEFAULT_MAX_RESIDENT_PAGES = 12;
    public static final long DEFAULT_SEED = 0x5EED_C0FFEE_2026L;
    private static final int REQUEST_RADIUS = 1;

    private final long seed;
    private final ContinuumWorldDomain domain;
    private final ContinuumMaterializer materializer;
    private final int pageSide;
    private final int maxResidentPages;
    private final long maxResidentPayloadBytes;
    private final int maxResolutionLevel;

    private ContinuumResolution resolution = ContinuumResolution.exact();
    private ContinuumPageLayout layout;
    private ContinuumScalarPageCache cache;
    private ContinuumPageKey focus;
    private long focusWorldX;
    private long focusWorldY;
    private List<ContinuumPageKey> requestedKeys = List.of();
    private Map<ContinuumPageKey, Double> requestedValues = Map.of();
    private List<ContinuumPageKey> lastEvictedKeys = List.of();

    public static ContinuumInspectorModel standard() {
        return standard(DEFAULT_SEED);
    }

    public static ContinuumInspectorModel standard(long seed) {
        ContinuumRandom random = new ContinuumRandom(seed);
        ContinuumScalarField field = (x, y) -> random.sampleUnit(
                "continuum-inspector",
                x >>> 6,
                y >>> 6,
                0L);
        return new ContinuumInspectorModel(
                DEFAULT_LOGICAL_SIDE,
                DEFAULT_PAGE_SIDE,
                DEFAULT_MAX_RESIDENT_PAGES,
                seed,
                field);
    }

    ContinuumInspectorModel(
            long logicalSide,
            int pageSide,
            int maxResidentPages,
            long seed,
            ContinuumScalarField field) {
        if (logicalSide <= 0L) throw new IllegalArgumentException("logicalSide must be > 0");
        if (pageSide <= 0) throw new IllegalArgumentException("pageSide must be > 0");
        if (maxResidentPages < 9) {
            throw new IllegalArgumentException("maxResidentPages must fit the 3x3 requested neighborhood");
        }
        if (field == null) throw new IllegalArgumentException("field must not be null");

        this.seed = seed;
        this.pageSide = pageSide;
        this.maxResidentPages = maxResidentPages;
        this.domain = new ContinuumWorldDomain(logicalSide, logicalSide);
        this.materializer = new ContinuumMaterializer(domain, field);
        long fullPagePayload = Math.multiplyExact(Math.multiplyExact((long) pageSide, pageSide), Double.BYTES);
        this.maxResidentPayloadBytes = Math.multiplyExact(fullPagePayload, (long) maxResidentPages);
        this.maxResolutionLevel = floorLog2(Math.max(domain.width(), domain.height()));
        this.focusWorldX = (domain.width() - 1L) / 2L;
        this.focusWorldY = (domain.height() - 1L) / 2L;
        rebuildRepresentation();
    }

    public long seed() {
        return seed;
    }

    public ContinuumPageKey focus() {
        return focus;
    }

    public int resolutionLevel() {
        return resolution.level();
    }

    public int maxResolutionLevel() {
        return maxResolutionLevel;
    }

    public long sampleStep() {
        return resolution.step();
    }

    public long pageWorldSpanX() {
        return layout.pageWorldSpanX();
    }

    public long pageWorldSpanY() {
        return layout.pageWorldSpanY();
    }

    public long pageCountX() {
        return layout.pageCountX();
    }

    public long pageCountY() {
        return layout.pageCountY();
    }

    public int pageSide() {
        return layout.pageWidth();
    }

    public long logicalWidth() {
        return domain.width();
    }

    public long logicalHeight() {
        return domain.height();
    }

    public long focusWorldX() {
        return focusWorldX;
    }

    public long focusWorldY() {
        return focusWorldY;
    }

    public List<ContinuumPageKey> requestedKeys() {
        return requestedKeys;
    }

    public List<ContinuumPageKey> residentKeys() {
        return cache.residentKeys();
    }

    public List<ContinuumPageKey> lastEvictedKeys() {
        return lastEvictedKeys;
    }

    public ContinuumPageCacheMetrics metrics() {
        return cache.metrics();
    }

    public OptionalDouble requestedValue(ContinuumPageKey key) {
        Double value = requestedValues.get(key);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public void moveFocus(long deltaPageX, long deltaPageY) {
        jumpToPage(
                saturatingAdd(focus.pageX(), deltaPageX),
                saturatingAdd(focus.pageY(), deltaPageY));
    }

    public void jumpToPage(long pageX, long pageY) {
        long clampedX = clamp(pageX, 0L, layout.pageCountX() - 1L);
        long clampedY = clamp(pageY, 0L, layout.pageCountY() - 1L);
        ContinuumPageKey next = new ContinuumPageKey(clampedX, clampedY);
        if (next.equals(focus)) return;
        focus = next;
        var window = layout.windowFor(focus);
        focusWorldX = window.xAt(window.width() / 2);
        focusWorldY = window.yAt(window.height() / 2);
        refreshRequestedNeighborhood();
    }

    public void coarsenResolution() {
        setResolutionLevel(resolution.level() + 1);
    }

    public void refineResolution() {
        setResolutionLevel(resolution.level() - 1);
    }

    public void setResolutionLevel(int level) {
        int clamped = (int) clamp(level, 0L, maxResolutionLevel);
        if (clamped == resolution.level()) return;
        resolution = new ContinuumResolution(clamped);
        rebuildRepresentation();
    }

    public void resetCenter() {
        focusWorldX = (domain.width() - 1L) / 2L;
        focusWorldY = (domain.height() - 1L) / 2L;
        focus = layout.pageAt(focusWorldX, focusWorldY);
        refreshRequestedNeighborhood();
    }

    private void rebuildRepresentation() {
        layout = new ContinuumPageLayout(domain, pageSide, pageSide, resolution);
        cache = new ContinuumScalarPageCache(
                layout,
                materializer,
                maxResidentPages,
                maxResidentPayloadBytes);
        focus = layout.pageAt(focusWorldX, focusWorldY);
        requestedKeys = List.of();
        requestedValues = Map.of();
        lastEvictedKeys = List.of();
        refreshRequestedNeighborhood();
    }

    private void refreshRequestedNeighborhood() {
        List<ContinuumPageKey> before = cache.residentKeys();
        List<ContinuumPageKey> requested = new ArrayList<>(9);
        Map<ContinuumPageKey, Double> values = new LinkedHashMap<>(9);

        for (long dy = -REQUEST_RADIUS; dy <= REQUEST_RADIUS; dy++) {
            long pageY = focus.pageY() + dy;
            if (pageY < 0L || pageY >= layout.pageCountY()) continue;
            for (long dx = -REQUEST_RADIUS; dx <= REQUEST_RADIUS; dx++) {
                long pageX = focus.pageX() + dx;
                if (pageX < 0L || pageX >= layout.pageCountX()) continue;

                ContinuumPageKey key = new ContinuumPageKey(pageX, pageY);
                ContinuumScalarPage page = cache.page(key);
                requested.add(key);
                values.put(key, page.sample(page.window().width() / 2, page.window().height() / 2));
            }
        }

        Set<ContinuumPageKey> after = new HashSet<>(cache.residentKeys());
        List<ContinuumPageKey> evicted = new ArrayList<>();
        for (ContinuumPageKey key : before) {
            if (!after.contains(key)) evicted.add(key);
        }

        requestedKeys = List.copyOf(requested);
        requestedValues = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        lastEvictedKeys = List.copyOf(evicted);
    }

    private static int floorLog2(long positive) {
        return 63 - Long.numberOfLeadingZeros(positive);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(value, max));
    }

    private static long saturatingAdd(long value, long delta) {
        if (delta > 0L && value > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        if (delta < 0L && value < Long.MIN_VALUE - delta) return Long.MIN_VALUE;
        return value + delta;
    }
}
