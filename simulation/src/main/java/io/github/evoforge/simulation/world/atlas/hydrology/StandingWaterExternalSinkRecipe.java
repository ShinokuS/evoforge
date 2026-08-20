package io.github.evoforge.simulation.world.atlas.hydrology;

/** Policy for deciding whether edge-connected water acts as an external drainage sink. */
public record StandingWaterExternalSinkRecipe(
        int boundaryContactSpanFactor,
        int minimumBoundaryContactCells,
        int minimumClearanceCells) {

    public StandingWaterExternalSinkRecipe {
        if (boundaryContactSpanFactor <= 0) {
            throw new IllegalArgumentException("external-sink boundary-contact factor must be positive");
        }
        if (minimumBoundaryContactCells <= 0 || minimumClearanceCells <= 0) {
            throw new IllegalArgumentException("external-sink absolute minima must be positive");
        }
    }

    public static StandingWaterExternalSinkRecipe balanced() {
        return new StandingWaterExternalSinkRecipe(
                20,
                8,
                2);
    }
}
