package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumMaterializer;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicalContinuumField;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysics;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class MacroGeophysicalScaleProfileTest {
    private static final int SAMPLE_SIDE = 128;
    private static final long EXPECTED_SAMPLES = (long) SAMPLE_SIDE * SAMPLE_SIDE;
    private static final long SAMPLE_STEP = 8_192L;
    private static final long MAX_MATERIALIZE_NANOS = Duration.ofSeconds(5).toNanos();

    @Test
    @Tag("scale-profile")
    void localMacroGeophysicalWorkDoesNotScaleWithLogicalWorldArea() {
        for (long logicalSide : List.of(16_000_000L, 1_000_000_000L, 1_000_000_000_000L)) {
            ContinuumWorldDomain domain = new ContinuumWorldDomain(logicalSide, logicalSide);
            ContinuumScalarField source = new MacroGeophysicalContinuumField(MacroGeophysics.create(
                    0x45A10F0E2026L,
                    1L,
                    MacroGeophysicsPreset.BALANCED.definition()));
            AtomicLong calls = new AtomicLong();
            ContinuumScalarField counted = (x, y) -> {
                calls.incrementAndGet();
                return source.sample(x, y);
            };
            ContinuumMaterializer materializer = new ContinuumMaterializer(domain, counted);
            ContinuumSampleWindow window = new ContinuumSampleWindow(
                    logicalSide / 3L,
                    logicalSide / 4L,
                    SAMPLE_SIDE,
                    SAMPLE_SIDE,
                    SAMPLE_STEP);

            long started = System.nanoTime();
            var page = materializer.materialize(window);
            long elapsed = System.nanoTime() - started;

            assertEquals(EXPECTED_SAMPLES, calls.get());
            assertEquals(EXPECTED_SAMPLES, page.copySamples().length);
            assertTrue(elapsed < MAX_MATERIALIZE_NANOS, "macro geophysics exceeded generous 5s gate");

            System.out.println("macro-geophysical-profile"
                    + " logicalSide=" + logicalSide
                    + " samples=" + calls.get()
                    + " step=" + SAMPLE_STEP
                    + " elapsedMs=" + elapsed / 1_000_000L);
        }
    }
}
