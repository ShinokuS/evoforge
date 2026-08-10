package io.github.evoforge.simulation.world.landscape.definition;

import java.nio.file.Path;

import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCompilerRegistry;
import io.github.evoforge.simulation.definition.DefinitionDirectoryLoader;
import io.github.evoforge.simulation.definition.DefinitionFileReader;
import io.github.evoforge.simulation.definition.DefinitionLoader;
import io.github.evoforge.simulation.definition.DefinitionRegistry;

public final class LandscapeDefinitionBootstrap {

    private final DefinitionRegistry<LandscapeDefinitionId> definitions;
    private final DefinitionDirectoryLoader<LandscapeDefinitionId> loader;

    private boolean used;

    @SafeVarargs
    public LandscapeDefinitionBootstrap(
            DefinitionAspectCompiler<LandscapeDefinitionId>... compilers) {

        if (compilers == null) {
            throw new IllegalArgumentException(
                    "compilers must not be null");
        }

        definitions = new DefinitionRegistry<>(
                LandscapeDefinitionId::of,
                LandscapeDefinitionId::asInt);

        DefinitionCompilerRegistry<LandscapeDefinitionId> compilerRegistry =
                new DefinitionCompilerRegistry<>();

        for (DefinitionAspectCompiler<LandscapeDefinitionId> compiler
                : compilers) {
            compilerRegistry.register(compiler);
        }

        loader = new DefinitionDirectoryLoader<>(
                new DefinitionFileReader(),
                new DefinitionLoader<>(
                        definitions,
                        compilerRegistry));
    }

    public DefinitionRegistry<LandscapeDefinitionId> load(Path root) {
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
