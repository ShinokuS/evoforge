package io.github.evoforge.visualizer.scenario;

/** Initial presentation focus for one visualizer scenario. */
public record ScenarioView(
        int selectedZ,
        float cameraX,
        float cameraY,
        float zoom) {

    public static final ScenarioView DEFAULT =
            new ScenarioView(1, 0f, 0f, 1f);

    public ScenarioView {
        if (!Float.isFinite(cameraX) || !Float.isFinite(cameraY)) {
            throw new IllegalArgumentException(
                    "camera coordinates must be finite");
        }
        if (!Float.isFinite(zoom) || zoom <= 0f) {
            throw new IllegalArgumentException(
                    "zoom must be finite and > 0");
        }
    }
}
