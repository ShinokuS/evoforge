package io.github.evoforge.visualizer.continuum;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.continuum.query.ContinuumLocalQueryBatch;
import io.github.evoforge.simulation.world.continuum.query.ContinuumLocalQueryRequest;
import io.github.evoforge.simulation.world.continuum.query.ContinuumLocalQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Plain-language Stage 1 demonstration model: many local requests share a small set of calculations. */
public final class ContinuumLocalQueryInspectorModel {
    public static final long LOGICAL_SIDE = 1_000_000L;
    public static final int PAGE_SIDE = 64;
    private static final long PAGE_BYTES = (long) PAGE_SIDE * PAGE_SIDE * Double.BYTES;

    private final ContinuumWorldDomain domain = new ContinuumWorldDomain(LOGICAL_SIDE, LOGICAL_SIDE);
    private final ContinuumPageLayout layout = new ContinuumPageLayout(domain, PAGE_SIDE, PAGE_SIDE);
    private final ContinuumMaterializer materializer = new ContinuumMaterializer(
            domain, (x, y) -> x * 0.25d + y * 0.5d);

    private int consumerCount = 10;
    private long revision;
    private long boundaryX = alignedCenter();
    private long boundaryY = alignedCenter();
    private List<ContinuumLocalQueryRequest> requests = List.of();
    private ContinuumLocalQueryBatch batch;

    public ContinuumLocalQueryInspectorModel() {
        refreshCleanProof();
    }

    public int consumerCount() {
        return consumerCount;
    }

    public long revision() {
        return revision;
    }

    public long boundaryX() {
        return boundaryX;
    }

    public long boundaryY() {
        return boundaryY;
    }

    public List<ContinuumLocalQueryRequest> requests() {
        return requests;
    }

    public Set<ContinuumPageKey> sharedRegions() {
        return batch.sharedRegions();
    }

    public ContinuumLocalQueryBatch batch() {
        return batch;
    }

    public ContinuumSampleWindow regionWindow(ContinuumPageKey key) {
        return layout.windowFor(key);
    }

    public void setConsumerCount(int count) {
        if (count != 1 && count != 10 && count != 100) {
            throw new IllegalArgumentException("demo consumer count must be 1, 10 or 100");
        }
        if (consumerCount == count) return;
        consumerCount = count;
        refreshCleanProof();
    }

    public void moveByPages(long dx, long dy) {
        long nextX = clampBoundary(boundaryX + dx * PAGE_SIDE);
        long nextY = clampBoundary(boundaryY + dy * PAGE_SIDE);
        if (nextX == boundaryX && nextY == boundaryY) return;
        boundaryX = nextX;
        boundaryY = nextY;
        refreshCleanProof();
    }

    public void advanceRevision() {
        revision++;
        refreshCleanProof();
    }

    public void resetCenter() {
        boundaryX = alignedCenter();
        boundaryY = alignedCenter();
        refreshCleanProof();
    }

    private void refreshCleanProof() {
        ContinuumLocalQueryService service = new ContinuumLocalQueryService(
                layout, materializer, 8, PAGE_BYTES * 8L, revision);
        requests = buildRequests();
        batch = service.queryBatch(requests);
    }

    private List<ContinuumLocalQueryRequest> buildRequests() {
        List<ContinuumLocalQueryRequest> result = new ArrayList<>(consumerCount);
        for (int i = 0; i < consumerCount; i++) {
            int jitterX = (i % 7) - 3;
            int jitterY = ((i / 7) % 7) - 3;
            long minX = boundaryX - 16L + jitterX;
            long minY = boundaryY - 16L + jitterY;
            result.add(new ContinuumLocalQueryRequest(
                    "consumer-" + (i + 1),
                    new ContinuumSampleWindow(minX, minY, 32, 32, 1L),
                    revision));
        }
        return List.copyOf(result);
    }

    private static long alignedCenter() {
        return (LOGICAL_SIDE / 2L / PAGE_SIDE) * PAGE_SIDE;
    }

    private static long clampBoundary(long candidate) {
        long min = PAGE_SIDE;
        long max = ((LOGICAL_SIDE - PAGE_SIDE - 1L) / PAGE_SIDE) * PAGE_SIDE;
        return Math.max(min, Math.min(candidate, max));
    }
}
