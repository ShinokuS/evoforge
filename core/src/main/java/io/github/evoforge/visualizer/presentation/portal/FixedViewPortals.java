package io.github.evoforge.visualizer.presentation.portal;

import java.util.ArrayList;
import java.util.List;

/** Small immutable presentation registry used by scenarios until a Structure domain owns portals. */
public final class FixedViewPortals implements ViewPortalLookup {

    private final List<ViewPortal> portals;

    public FixedViewPortals(ViewPortal... portals) {
        if (portals == null) {
            throw new IllegalArgumentException("portals must not be null");
        }
        List<ViewPortal> copy = new ArrayList<>(portals.length);
        for (ViewPortal portal : portals) {
            if (portal == null) {
                throw new IllegalArgumentException("portal must not be null");
            }
            for (ViewPortal existing : copy) {
                if (existing.id().equals(portal.id())) {
                    throw new IllegalArgumentException("duplicate portal id: " + portal.id());
                }
                if (existing.isSurfaceAt(portal.surfaceX(), portal.surfaceY())) {
                    throw new IllegalArgumentException("duplicate surface portal cell");
                }
            }
            copy.add(portal);
        }
        this.portals = List.copyOf(copy);
    }

    @Override
    public ViewPortal surfaceAt(int x, int y) {
        for (ViewPortal portal : portals) {
            if (portal.isSurfaceAt(x, y)) return portal;
        }
        return null;
    }

    @Override
    public ViewPortal interiorAt(String interiorId, int x, int y, int z) {
        if (interiorId == null) return null;
        for (ViewPortal portal : portals) {
            if (portal.isInteriorAt(interiorId, x, y, z)) return portal;
        }
        return null;
    }

    @Override
    public void forEach(ViewPortalConsumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }
        for (ViewPortal portal : portals) consumer.accept(portal);
    }
}
