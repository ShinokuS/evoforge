package io.github.evoforge.visualizer.presentation.portal;

/** Read-only portal presentation used by world rendering and interaction. */
public interface ViewPortalLookup {

    ViewPortalLookup EMPTY = new ViewPortalLookup() {
        @Override public ViewPortal surfaceAt(int x, int y) { return null; }
        @Override public ViewPortal interiorAt(String interiorId, int x, int y, int z) { return null; }
        @Override public void forEach(ViewPortalConsumer consumer) { }
    };

    ViewPortal surfaceAt(int x, int y);

    ViewPortal interiorAt(String interiorId, int x, int y, int z);

    void forEach(ViewPortalConsumer consumer);

    @FunctionalInterface
    interface ViewPortalConsumer {
        void accept(ViewPortal portal);
    }
}
