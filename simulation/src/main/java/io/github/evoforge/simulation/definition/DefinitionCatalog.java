package io.github.evoforge.simulation.definition;

public interface DefinitionCatalog {
    DefinitionId resolve(String key);

    String keyOf(DefinitionId id);

    boolean contains(DefinitionId id);
}
