package io.github.evoforge.simulation.world.landscape.definition;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionRegistry;

final class LandscapeDefinitionBootstrapTest {

    @TempDir
    Path directory;

    @Test
    void loadsLandscapeDefinitions() throws IOException {
        Files.writeString(
                directory.resolve("granite.json"),
                """
                        {
                            "key": "core:granite",
                            "aspects": {}
                        }
                        """,
                UTF_8);

        LandscapeDefinitionBootstrap bootstrap =
                new LandscapeDefinitionBootstrap();

        DefinitionRegistry<LandscapeDefinitionId> definitions =
                bootstrap.load(directory);

        assertEquals(
                LandscapeDefinitionId.of(0),
                definitions.resolve("core:granite"));
        assertTrue(definitions.isFrozen());
    }

    @Test
    void rejectsSecondLoad() {
        LandscapeDefinitionBootstrap bootstrap =
                new LandscapeDefinitionBootstrap();

        bootstrap.load(directory);

        assertThrows(
                IllegalStateException.class,
                () -> bootstrap.load(directory));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsNullCompilers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandscapeDefinitionBootstrap(
                        (DefinitionAspectCompiler<LandscapeDefinitionId>[]) null));
    }
}
