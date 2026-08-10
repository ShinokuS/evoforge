package io.github.evoforge.simulation.definition;

import com.google.gson.JsonObject;

public interface DefinitionAspectCompiler {

    String key();

    void compile(
            DefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog catalog);

    default void finish() {
    }
}
