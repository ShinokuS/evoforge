package io.github.evoforge.simulation.world.continuum.query;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageCacheMetrics;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.continuum.page.ContinuumScalarPageCache;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 1 proof service for bounded local reads with shared regional work.
 *
 * <p>Consumers never receive cached pages directly. The service deduplicates the technical regions
 * needed by a batch, loads each unique region once, then returns a clipped immutable local view for
 * every consumer.</p>
 */
public final class ContinuumLocalQueryService {
    private final ContinuumPageLayout layout;
    private final ContinuumMaterializer materializer;
    private final int maxResidentPages;
    private final long maxResidentPayloadBytes;
    private volatile State state;

    public ContinuumLocalQueryService(
            ContinuumPageLayout layout,
            ContinuumMaterializer materializer,
            int maxResidentPages,
            long maxResidentPayloadBytes,
            long initialRevision) {
        if (layout == null || materializer == null) {
            throw new IllegalArgumentException("layout and materializer must not be null");
        }
        if (!layout.domain().equals(materializer.domain())) {
            throw new IllegalArgumentException("layout and materializer must use the same logical domain");
        }
        if (maxResidentPages <= 0 || maxResidentPayloadBytes <= 0L) {
            throw new IllegalArgumentException("cache budgets must be > 0");
        }
        if (initialRevision < 0L) {
            throw new IllegalArgumentException("initialRevision must be >= 0");
        }
        this.layout = layout;
        this.materializer = materializer;
        this.maxResidentPages = maxResidentPages;
        this.maxResidentPayloadBytes = maxResidentPayloadBytes;
        this.state = new State(initialRevision, newCache());
    }

    public long currentRevision() {
        return state.revision();
    }

    /**
     * Invalidates all reusable regional representation by moving to a strictly newer revision.
     * In-flight work from the old state may finish internally but is never returned as current data.
     */
    public synchronized void advanceRevision(long newRevision) {
        if (newRevision <= state.revision()) {
            throw new IllegalArgumentException("newRevision must be greater than the current revision");
        }
        state = new State(newRevision, newCache());
    }

    public ContinuumPageCacheMetrics cacheMetrics() {
        return state.cache().metrics();
    }

    public ContinuumLocalQueryBatch queryBatch(List<ContinuumLocalQueryRequest> requests) {
        if (requests == null) {
            throw new IllegalArgumentException("requests must not be null");
        }
        long started = System.nanoTime();
        State snapshot = state;

        if (requests.isEmpty()) {
            ContinuumPageCacheMetrics cache = snapshot.cache().metrics();
            return new ContinuumLocalQueryBatch(
                    snapshot.revision(),
                    List.of(),
                    Set.of(),
                    new ContinuumLocalQueryMetrics(
                            0, 0, 0, 0, 0, 0, 0, 0,
                            cache.residentPages(), cache.residentPayloadBytes(),
                            System.nanoTime() - started));
        }

        LinkedHashMap<ContinuumLocalQueryRequest, List<ContinuumPageKey>> regionsByRequest =
                new LinkedHashMap<>();
        LinkedHashSet<ContinuumPageKey> uniqueRegions = new LinkedHashSet<>();
        int totalRegionUses = 0;

        for (ContinuumLocalQueryRequest request : requests) {
            validateRequest(request, snapshot.revision());
            List<ContinuumPageKey> regions = regionsFor(request.window());
            regionsByRequest.put(request, regions);
            uniqueRegions.addAll(regions);
            totalRegionUses = Math.addExact(totalRegionUses, regions.size());
        }

        ContinuumPageCacheMetrics before = snapshot.cache().metrics();
        Map<ContinuumPageKey, ContinuumScalarPage> sharedPages = new LinkedHashMap<>();
        for (ContinuumPageKey key : uniqueRegions) {
            sharedPages.put(key, snapshot.cache().page(key));
        }
        ContinuumPageCacheMetrics after = snapshot.cache().metrics();

        ensureStillCurrent(snapshot);

        List<ContinuumLocalScalarView> views = new ArrayList<>(requests.size());
        for (ContinuumLocalQueryRequest request : requests) {
            views.add(buildView(request, sharedPages));
        }

        ensureStillCurrent(snapshot);

        int reusedRegionUses = totalRegionUses - uniqueRegions.size();
        ContinuumLocalQueryMetrics metrics = new ContinuumLocalQueryMetrics(
                requests.size(),
                totalRegionUses,
                uniqueRegions.size(),
                reusedRegionUses,
                after.hits() - before.hits(),
                after.misses() - before.misses(),
                after.loads() - before.loads(),
                after.sharedWaits() - before.sharedWaits(),
                after.residentPages(),
                after.residentPayloadBytes(),
                System.nanoTime() - started);

        return new ContinuumLocalQueryBatch(snapshot.revision(), views, uniqueRegions, metrics);
    }

    private void validateRequest(ContinuumLocalQueryRequest request, long revision) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.revision() != revision) {
            throw new StaleContinuumQueryException(
                    "request revision " + request.revision() + " is not current revision " + revision);
        }

        ContinuumSampleWindow window = request.window();
        if (window.step() != layout.sampleStep()) {
            throw new IllegalArgumentException("request step must match the query layout resolution");
        }
        if (Math.floorMod(window.minX(), window.step()) != 0L
                || Math.floorMod(window.minY(), window.step()) != 0L) {
            throw new IllegalArgumentException("request must align to the layout sampling grid");
        }

        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!layout.domain().contains(window.minX(), window.minY())
                || !layout.domain().contains(maxX, maxY)) {
            throw new IllegalArgumentException("request lies outside the logical world domain");
        }
    }

    private List<ContinuumPageKey> regionsFor(ContinuumSampleWindow window) {
        ContinuumPageKey min = layout.pageAt(window.minX(), window.minY());
        ContinuumPageKey max = layout.pageAt(
                window.xAt(window.width() - 1),
                window.yAt(window.height() - 1));
        List<ContinuumPageKey> keys = new ArrayList<>();
        for (long pageY = min.pageY(); pageY <= max.pageY(); pageY++) {
            for (long pageX = min.pageX(); pageX <= max.pageX(); pageX++) {
                keys.add(new ContinuumPageKey(pageX, pageY));
            }
        }
        return List.copyOf(keys);
    }

    private ContinuumLocalScalarView buildView(
            ContinuumLocalQueryRequest request,
            Map<ContinuumPageKey, ContinuumScalarPage> sharedPages) {
        ContinuumSampleWindow requested = request.window();
        double[] samples = new double[Math.multiplyExact(requested.width(), requested.height())];

        for (int y = 0; y < requested.height(); y++) {
            long worldY = requested.yAt(y);
            for (int x = 0; x < requested.width(); x++) {
                long worldX = requested.xAt(x);
                ContinuumPageKey key = layout.pageAt(worldX, worldY);
                ContinuumScalarPage page = sharedPages.get(key);
                if (page == null) {
                    throw new IllegalStateException("shared page missing for requested coordinate");
                }
                ContinuumSampleWindow pageWindow = page.window();
                int pageX = Math.toIntExact((worldX - pageWindow.minX()) / pageWindow.step());
                int pageY = Math.toIntExact((worldY - pageWindow.minY()) / pageWindow.step());
                samples[y * requested.width() + x] = page.sample(pageX, pageY);
            }
        }

        return new ContinuumLocalScalarView(
                request.consumerId(), request.revision(), requested, samples);
    }

    private void ensureStillCurrent(State snapshot) {
        if (state != snapshot) {
            throw new StaleContinuumQueryException(
                    "world revision changed while local query was being resolved");
        }
    }

    private ContinuumScalarPageCache newCache() {
        return new ContinuumScalarPageCache(
                layout, materializer, maxResidentPages, maxResidentPayloadBytes);
    }

    private record State(long revision, ContinuumScalarPageCache cache) {
    }
}
