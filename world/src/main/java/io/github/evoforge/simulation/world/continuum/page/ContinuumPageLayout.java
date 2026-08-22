package io.github.evoforge.simulation.world.continuum.page;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.model.ContinuumResolution;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Maps global Continuum coordinates to configurable technical pages at one nested sampling resolution.
 * Page dimensions and resolution are representation policy and do not define natural world features.
 */
public final class ContinuumPageLayout {
    private final ContinuumWorldDomain domain;
    private final int pageWidth;
    private final int pageHeight;
    private final ContinuumResolution resolution;
    private final long pageWorldSpanX;
    private final long pageWorldSpanY;

    public ContinuumPageLayout(ContinuumWorldDomain domain, int pageWidth, int pageHeight) {
        this(domain, pageWidth, pageHeight, ContinuumResolution.exact());
    }

    public ContinuumPageLayout(
            ContinuumWorldDomain domain,
            int pageWidth,
            int pageHeight,
            ContinuumResolution resolution) {
        if (domain == null || resolution == null) {
            throw new IllegalArgumentException("domain and resolution must not be null");
        }
        if (pageWidth <= 0 || pageHeight <= 0) {
            throw new IllegalArgumentException("page dimensions must be > 0");
        }
        this.domain = domain;
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.resolution = resolution;
        this.pageWorldSpanX = Math.multiplyExact((long) pageWidth, resolution.step());
        this.pageWorldSpanY = Math.multiplyExact((long) pageHeight, resolution.step());
    }

    public ContinuumWorldDomain domain() {
        return domain;
    }

    public int pageWidth() {
        return pageWidth;
    }

    public int pageHeight() {
        return pageHeight;
    }

    public ContinuumResolution resolution() {
        return resolution;
    }

    public long sampleStep() {
        return resolution.step();
    }

    public long pageWorldSpanX() {
        return pageWorldSpanX;
    }

    public long pageWorldSpanY() {
        return pageWorldSpanY;
    }

    public long pageCountX() {
        return ((domain.width() - 1L) / pageWorldSpanX) + 1L;
    }

    public long pageCountY() {
        return ((domain.height() - 1L) / pageWorldSpanY) + 1L;
    }

    public ContinuumPageKey pageAt(long worldX, long worldY) {
        if (!domain.contains(worldX, worldY)) {
            throw new IllegalArgumentException("coordinate lies outside the logical world domain");
        }
        return new ContinuumPageKey(worldX / pageWorldSpanX, worldY / pageWorldSpanY);
    }

    public ContinuumSampleWindow windowFor(ContinuumPageKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (key.pageX() >= pageCountX() || key.pageY() >= pageCountY()) {
            throw new IllegalArgumentException("page lies outside the logical world domain");
        }

        long minX = Math.multiplyExact(key.pageX(), pageWorldSpanX);
        long minY = Math.multiplyExact(key.pageY(), pageWorldSpanY);
        int width = sampleCount(domain.width() - minX, pageWidth, resolution.step());
        int height = sampleCount(domain.height() - minY, pageHeight, resolution.step());
        return new ContinuumSampleWindow(minX, minY, width, height, resolution.step());
    }

    public long payloadBytesFor(ContinuumPageKey key) {
        ContinuumSampleWindow window = windowFor(key);
        long samples = Math.multiplyExact((long) window.width(), window.height());
        return Math.multiplyExact(samples, Double.BYTES);
    }

    private static int sampleCount(long remainingWorldUnits, int maxSamples, long step) {
        long availableSamples = ((remainingWorldUnits - 1L) / step) + 1L;
        return (int) Math.min((long) maxSamples, availableSamples);
    }
}
