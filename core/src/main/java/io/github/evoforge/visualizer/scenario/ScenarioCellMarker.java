package io.github.evoforge.visualizer.scenario;

/** One presentation-only cell marker in a focused debug scenario. */
public record ScenarioCellMarker(
        int x,
        int y,
        int z,
        ScenarioCellMarkerStyle style) {

    public ScenarioCellMarker {
        if (style == null) {
            throw new IllegalArgumentException("style must not be null");
        }
    }
}
