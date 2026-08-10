package io.github.evoforge.simulation.world.object.definition;

import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCompilerRegistry;
import io.github.evoforge.simulation.definition.DefinitionDirectoryLoader;
import io.github.evoforge.simulation.definition.DefinitionFileReader;
import io.github.evoforge.simulation.definition.DefinitionLoader;
import io.github.evoforge.simulation.definition.DefinitionRegistry;

import java.nio.file.Path;

public final class ObjectDefinitionBootstrap {

    private final DefinitionRegistry definitions;
    private final DefinitionDirectoryLoader loader;

    private boolean used;

    public ObjectDefinitionBootstrap(
            DefinitionAspectCompiler... compilers) {

        if (compilers == null) {
            throw new IllegalArgumentException(
                    "compilers must not be null");
        }

        definitions = new DefinitionRegistry();

        DefinitionCompilerRegistry compilerRegistry = new DefinitionCompilerRegistry();

        for (DefinitionAspectCompiler compiler : compilers) {
            compilerRegistry.register(compiler);
        }

        loader = new DefinitionDirectoryLoader(
                new DefinitionFileReader(),
                new DefinitionLoader(
                        definitions,
                        compilerRegistry));
    }

    public DefinitionRegistry load(Path root) {
        if (used) {
            throw new IllegalStateException(
                    "bootstrap has already been used");
        }

        used = true;

        loader.load(root);
        definitions.freeze();

        return definitions;
    }
}
