package io.github.evoforge.simulation.world.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Emits compact structured summaries for explicit generated-world audit checkpoints. */
public final class GeneratedWorldDiagnosticsLog {

    private static final Logger LOG = LoggerFactory.getLogger(
            GeneratedWorldDiagnosticsLog.class);

    private GeneratedWorldDiagnosticsLog() {
    }

    public static void info(GeneratedWorldDiagnostics diagnostics) {
        LOG.info("{}", GeneratedWorldDiagnosticsFormat.line(diagnostics));
    }
}
