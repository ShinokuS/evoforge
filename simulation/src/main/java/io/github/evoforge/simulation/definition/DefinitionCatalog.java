package io.github.evoforge.simulation.definition;

public interface DefinitionCatalog<I> {
    I resolve(String key);

    String keyOf(I id);

    boolean contains(I id);
}
