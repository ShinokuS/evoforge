package io.github.evoforge.simulation.world.continuum.page;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Maps global Continuum coordinates to configurable technical pages.
 * Page dimensions are representation policy and do not define natural world features.
 */
public final class ContinuumPageLayout {
    private final ContinuumWorldDomain domain;
    private final int pageWidth;
    private final int pageHeight;

    public ContinuumPageLayout(ContinuumWorldDomain domain, int pageWidth, int pageHeight) {
        if (domain == null) {
            throw new IllegalArgumentException("domain must not be null");
        }
        if (pageWidth <= 0 || pageHeight <= 0) {
            throw new IllegalArgumentException("page dimensions must be > 0");
        }
        this.domain = domain;
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
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

    public long pageCountX() {
        return ((domain.width() - 1L) / pageWidth) + 1L;
    }

    public long pageCountY() {
        return ((domain.height() - 1L) / pageHeight) + 1L;
    }

    public ContinuumPageKey pageAt(long worldX, long worldY) {
        if (!domain.contains(worldX, worldY)) {
            throw new IllegalArgumentException("coordinate lies outside the logical world domain");
        }
        return new ContinuumPageKey(worldX / pageWidth, worldY / pageHeight);
    }

    public ContinuumSampleWindow windowFor(ContinuumPageKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (key.pageX() >= pageCountX() || key.pageY() >= pageCountY()) {
            throw new IllegalArgumentException("page lies outside the logical world domain");
        }

        long minX = Math.multiplyExact(key.pageX(), (long) pageWidth);
        long minY = Math.multiplyExact(key.pageY(), (long) pageHeight);
        int width = (int) Math.min((long) pageWidth, domain.width() - minX);
        int height = (int) Math.min((long) pageHeight, domain.height() - minY);
        return new ContinuumSampleWindow(minX, minY, width, height, 1L);
    }

    public long payloadBytesFor(ContinuumPageKey key) {
        ContinuumSampleWindow window = windowFor(key);
        long samples = Math.multiplyExact((long) window.width(), window.height());
        return Math.multiplyExact(samples, Double.BYTES);
    }
}
