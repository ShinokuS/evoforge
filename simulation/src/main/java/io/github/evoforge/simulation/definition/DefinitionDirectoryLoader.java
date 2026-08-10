package io.github.evoforge.simulation.definition;

import java.nio.file.Path;

public final class DefinitionDirectoryLoader<I> {

    private final DefinitionFileReader reader;
    private final DefinitionLoader<I> loader;

    public DefinitionDirectoryLoader(
            DefinitionFileReader reader,
            DefinitionLoader<I> loader) {
        if (reader == null) {
            throw new IllegalArgumentException(
                    "reader must not be null");
        }

        if (loader == null) {
            throw new IllegalArgumentException(
                    "loader must not be null");
        }

        this.reader = reader;
        this.loader = loader;
    }

    public void load(Path root) {
        loader.load(reader.read(root));
    }
}
