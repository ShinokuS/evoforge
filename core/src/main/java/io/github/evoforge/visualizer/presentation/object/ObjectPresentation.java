package io.github.evoforge.visualizer.presentation.object;

/** Immutable presentation metadata bound outside simulation to one object definition. */
public record ObjectPresentation(
        String displayName,
        String description,
        ObjectVisualFamily family,
        int variant) {

    public ObjectPresentation {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (description == null) throw new IllegalArgumentException("description must not be null");
        if (family == null) throw new IllegalArgumentException("family must not be null");
        if (variant < 0) throw new IllegalArgumentException("variant must be >= 0");
    }
}
