package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class ContinuumScaleProfileTest {
    private static final long MAX_SETUP_NANOS = Duration.ofSeconds(2).toNanos();
    private static final long MAX_COLD_NANOS = Duration.ofSeconds(10).toNanos();
    private static final long MAX_WARM_NANOS = Duration.ofSeconds(5).toNanos();

    @Test
    @Tag("scale-profile")
    void profile10k100kAnd1mWithConstantResidentWorkingSet() {
        List<Long> logicalSides = List.of(10_000L, 100_000L, 1_000_000L);
        Long expectedColdChecksum = null;
        Long expectedWarmChecksum = null;

        for (long logicalSide : logicalSides) {
            ContinuumScaleWorkload.RunResult result = ContinuumScaleWorkload.run(logicalSide);
            System.out.println(result.report());

            assertEquals(ContinuumScaleWorkload.MAX_RESIDENT_PAGES, result.afterCold().residentPages(),
                    "cold resident pages, side=" + logicalSide);
            assertEquals(ContinuumScaleWorkload.MAX_RESIDENT_PAYLOAD_BYTES, result.afterCold().residentPayloadBytes(),
                    "cold resident payload, side=" + logicalSide);
            assertEquals(ContinuumScaleWorkload.COLD_PAGE_REQUESTS, result.afterCold().loads(),
                    "cold loads, side=" + logicalSide);
            assertEquals(ContinuumScaleWorkload.COLD_PAGE_REQUESTS, result.afterCold().misses(),
                    "cold misses, side=" + logicalSide);
            assertEquals(
                    ContinuumScaleWorkload.COLD_PAGE_REQUESTS - ContinuumScaleWorkload.MAX_RESIDENT_PAGES,
                    result.afterCold().evictions(),
                    "cold evictions, side=" + logicalSide);

            assertEquals(result.afterCold().loads(), result.afterWarm().loads(),
                    "warm lookup must not rematerialize, side=" + logicalSide);
            assertEquals(result.afterCold().misses(), result.afterWarm().misses(),
                    "warm lookup must not miss, side=" + logicalSide);
            assertEquals(ContinuumScaleWorkload.WARM_LOOKUPS, result.afterWarm().hits(),
                    "warm hits, side=" + logicalSide);
            assertEquals(ContinuumScaleWorkload.MAX_RESIDENT_PAGES, result.afterWarm().residentPages(),
                    "warm resident pages, side=" + logicalSide);
            assertEquals(ContinuumScaleWorkload.MAX_RESIDENT_PAYLOAD_BYTES, result.afterWarm().residentPayloadBytes(),
                    "warm resident payload, side=" + logicalSide);

            if (expectedColdChecksum == null) {
                expectedColdChecksum = result.coldChecksum();
                expectedWarmChecksum = result.warmChecksum();
            } else {
                assertEquals(expectedColdChecksum.longValue(), result.coldChecksum(),
                        "logical world size must not change local cold samples");
                assertEquals(expectedWarmChecksum.longValue(), result.warmChecksum(),
                        "logical world size must not change local warm samples");
            }

            assertTrue(result.setupNanos() < MAX_SETUP_NANOS,
                    "Continuum setup exceeded generous 2s gate, side=" + logicalSide);
            assertTrue(result.coldNanos() < MAX_COLD_NANOS,
                    "cold materialization exceeded generous 10s gate, side=" + logicalSide);
            assertTrue(result.warmNanos() < MAX_WARM_NANOS,
                    "warm lookup exceeded generous 5s gate, side=" + logicalSide);
        }
    }
}
