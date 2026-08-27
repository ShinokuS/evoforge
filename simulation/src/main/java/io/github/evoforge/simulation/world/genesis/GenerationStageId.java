package io.github.evoforge.simulation.world.genesis;

/** Stable semantic identifier for one causal world-generation stage. */
public record GenerationStageId(String value) {
    public GenerationStageId {
        value = GenesisKeyFormat.requireKey(value, "generation stage id");
    }

    public static GenerationStageId of(String value) {
        return new GenerationStageId(value);
    }
}
