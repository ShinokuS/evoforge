package io.github.evoforge.simulation.world.material;

import java.nio.file.Path;

import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCompilerRegistry;
import io.github.evoforge.simulation.definition.DefinitionDirectoryLoader;
import io.github.evoforge.simulation.definition.DefinitionFileReader;
import io.github.evoforge.simulation.definition.DefinitionLoader;
import io.github.evoforge.simulation.definition.DefinitionRegistry;

public final class MaterialDefinitionBootstrap {

    private final DefinitionRegistry<MaterialDefinitionId> definitions;
    private final DefinitionDirectoryLoader<MaterialDefinitionId> loader;

    private boolean used;

    @SafeVarargs
    public MaterialDefinitionBootstrap(
            DefinitionAspectCompiler<MaterialDefinitionId>... compilers) {

        if (compilers == null) {
            throw new IllegalArgumentException(
                    "compilers must not be null");
        }

        definitions = new DefinitionRegistry<>(
                MaterialDefinitionId::of,
                MaterialDefinitionId::asInt);

        DefinitionCompilerRegistry<MaterialDefinitionId> compilerRegistry =
                new DefinitionCompilerRegistry<>();

        for (DefinitionAspectCompiler<MaterialDefinitionId> compiler
                : compilers) {
            compilerRegistry.register(compiler);
        }

        loader = new DefinitionDirectoryLoader<>(
                new DefinitionFileReader(),
                new DefinitionLoader<>(
                        definitions,
                        compilerRegistry));
    }

    public DefinitionRegistry<MaterialDefinitionId> load(Path root) {
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
