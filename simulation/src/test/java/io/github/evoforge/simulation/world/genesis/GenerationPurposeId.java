package io.github.evoforge.simulation.world.genesis;

/** Stable semantic identifier for one independent random purpose inside a generation stage. */
public record GenerationPurposeId(String value) {
    public GenerationPurposeId {
        value = GenesisKeyFormat.requireKey(value, "generation purpose id");
    }

    public static GenerationPurposeId of(String value) {
        return new GenerationPurposeId(value);
    }
}
