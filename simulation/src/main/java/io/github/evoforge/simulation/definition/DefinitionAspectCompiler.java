package io.github.evoforge.simulation.definition;

import com.google.gson.JsonObject;

public interface DefinitionAspectCompiler<I> {

    String key();

    void compile(
            I definitionId,
            JsonObject data,
            DefinitionCatalog<I> catalog);

    default void finish() {
    }
}
