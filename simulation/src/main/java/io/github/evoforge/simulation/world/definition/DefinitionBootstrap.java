package io.github.evoforge.simulation.world.definition;

import java.nio.file.Path;

public final class DefinitionBootstrap {

    private final DefinitionRegistry definitions;
    private final DefinitionDirectoryLoader loader;

    public DefinitionBootstrap(
        DefinitionAspectCompiler... compilers
    ) {
        if (compilers == null) {
            throw new IllegalArgumentException(
                "compilers must not be null"
            );
        }

        definitions = new DefinitionRegistry();

        DefinitionCompilerRegistry compilerRegistry =
            new DefinitionCompilerRegistry();

        for (DefinitionAspectCompiler compiler : compilers) {
            compilerRegistry.register(compiler);
        }

        loader = new DefinitionDirectoryLoader(
            new DefinitionFileReader(),
            new DefinitionLoader(
                definitions,
                compilerRegistry
            )
        );
    }

    public DefinitionRegistry load(Path root) {
        loader.load(root);
        definitions.freeze();

        return definitions;
    }
}