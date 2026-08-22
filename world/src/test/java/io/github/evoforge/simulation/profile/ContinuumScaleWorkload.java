package io.github.evoforge.simulation.profile;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageCacheMetrics;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageLayout;
import io.github.evoforge.simulation.world.continuum.page.ContinuumScalarPageCache;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/** Repeatable Phase 0 workload proving that logical world scale does not expand the resident page working set. */
final class ContinuumScaleWorkload {
    static final int PAGE_SIDE = 256;
    static final int MAX_RESIDENT_PAGES = 4;
    static final int COLD_PAGE_REQUESTS = 8;
    static final int WARM_LOOKUPS = 100_000;
    static final long PAGE_PAYLOAD_BYTES = (long) PAGE_SIDE * PAGE_SIDE * Double.BYTES;
    static final long MAX_RESIDENT_PAYLOAD_BYTES = PAGE_PAYLOAD_BYTES * MAX_RESIDENT_PAGES;

    private ContinuumScaleWorkload() {}

    static RunResult run(long logicalSide) {
        if (logicalSide < (long) PAGE_SIDE * COLD_PAGE_REQUESTS) {
            throw new IllegalArgumentException("logicalSide is too small for the fixed profile workload");
        }

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long heapBefore = memory.getHeapMemoryUsage().getUsed();

        long setupStarted = System.nanoTime();
        ContinuumWorldDomain domain = new ContinuumWorldDomain(logicalSide, logicalSide);
        ContinuumPageLayout layout = new ContinuumPageLayout(domain, PAGE_SIDE, PAGE_SIDE);
        ContinuumScalarField field = (x, y) -> x * 0.25d + y * 0.5d;
        ContinuumMaterializer materializer = new ContinuumMaterializer(domain, field);
        ContinuumScalarPageCache cache = new ContinuumScalarPageCache(
                layout,
                materializer,
                MAX_RESIDENT_PAGES,
                MAX_RESIDENT_PAYLOAD_BYTES);
        long setupNanos = System.nanoTime() - setupStarted;
        long heapAfterSetup = memory.getHeapMemoryUsage().getUsed();

        long coldStarted = System.nanoTime();
        long coldChecksum = 0L;
        for (int pageX = 0; pageX < COLD_PAGE_REQUESTS; pageX++) {
            ContinuumScalarPage page = cache.page(new ContinuumPageKey(pageX, 0L));
            coldChecksum = mix(coldChecksum, page.sample(0, 0));
            coldChecksum = mix(coldChecksum, page.sample(PAGE_SIDE - 1, PAGE_SIDE - 1));
        }
        long coldNanos = System.nanoTime() - coldStarted;
        ContinuumPageCacheMetrics afterCold = cache.metrics();
        long heapAfterCold = memory.getHeapMemoryUsage().getUsed();

        var resident = cache.residentKeys();
        long warmStarted = System.nanoTime();
        long warmChecksum = 0L;
        for (int i = 0; i < WARM_LOOKUPS; i++) {
            ContinuumPageKey key = resident.get(i % resident.size());
            ContinuumScalarPage page = cache.page(key);
            warmChecksum = mix(warmChecksum, page.sample(i & (PAGE_SIDE - 1), (i >>> 8) & (PAGE_SIDE - 1)));
        }
        long warmNanos = System.nanoTime() - warmStarted;
        ContinuumPageCacheMetrics afterWarm = cache.metrics();
        long heapAfterWarm = memory.getHeapMemoryUsage().getUsed();

        return new RunResult(
                logicalSide,
                setupNanos,
                coldNanos,
                warmNanos,
                heapBefore,
                heapAfterSetup,
                heapAfterCold,
                heapAfterWarm,
                coldChecksum,
                warmChecksum,
                afterCold,
                afterWarm);
    }

    private static long mix(long current, double value) {
        long bits = Double.doubleToLongBits(value);
        return Long.rotateLeft(current, 7) ^ bits ^ 0x9E3779B97F4A7C15L;
    }

    record RunResult(
            long logicalSide,
            long setupNanos,
            long coldNanos,
            long warmNanos,
            long heapBeforeBytes,
            long heapAfterSetupBytes,
            long heapAfterColdBytes,
            long heapAfterWarmBytes,
            long coldChecksum,
            long warmChecksum,
            ContinuumPageCacheMetrics afterCold,
            ContinuumPageCacheMetrics afterWarm) {

        String report() {
            long coldSamples = (long) COLD_PAGE_REQUESTS * PAGE_SIDE * PAGE_SIDE;
            return "continuum-scale-profile"
                    + " logicalSide=" + logicalSide
                    + " logicalCells=" + Math.multiplyExact(logicalSide, logicalSide)
                    + " pageSide=" + PAGE_SIDE
                    + " residentPageBudget=" + MAX_RESIDENT_PAGES
                    + " residentPayloadBudgetBytes=" + MAX_RESIDENT_PAYLOAD_BYTES
                    + " setupMs=" + nanosToMillis(setupNanos)
                    + " coldMs=" + nanosToMillis(coldNanos)
                    + " coldNsPerSample=" + (coldSamples == 0L ? 0L : coldNanos / coldSamples)
                    + " warmMs=" + nanosToMillis(warmNanos)
                    + " warmNsPerLookup=" + (WARM_LOOKUPS == 0 ? 0L : warmNanos / WARM_LOOKUPS)
                    + " heapBeforeBytes=" + heapBeforeBytes
                    + " heapAfterSetupBytes=" + heapAfterSetupBytes
                    + " heapAfterColdBytes=" + heapAfterColdBytes
                    + " heapAfterWarmBytes=" + heapAfterWarmBytes
                    + " hits=" + afterWarm.hits()
                    + " misses=" + afterWarm.misses()
                    + " loads=" + afterWarm.loads()
                    + " evictions=" + afterWarm.evictions()
                    + " residentPages=" + afterWarm.residentPages()
                    + " residentPayloadBytes=" + afterWarm.residentPayloadBytes()
                    + " coldChecksum=" + Long.toUnsignedString(coldChecksum)
                    + " warmChecksum=" + Long.toUnsignedString(warmChecksum);
        }

        private static long nanosToMillis(long nanos) {
            return nanos / 1_000_000L;
        }
    }
}
