package io.github.evoforge.simulation.world.definition;

public interface DefinitionCatalog {
    DefinitionId resolve(String key);

    String keyOf(DefinitionId id);

    boolean contains(DefinitionId id);
}