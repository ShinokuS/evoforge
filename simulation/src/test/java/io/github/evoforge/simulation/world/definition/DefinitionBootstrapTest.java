package io.github.evoforge.simulation.world.definition;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionBootstrapTest {

    @TempDir
    Path directory;

    @Test
    void loadsDefinitionsAndFreezesRegistry()
            throws IOException {

        Files.writeString(
                directory.resolve("test.json"),
                """
                        {
                            "key": "core:test",
                            "aspects": {
                                "test-aspect": {
                                    "value": 42
                                }
                            }
                        }
                        """,
                UTF_8);

        TestCompiler compiler = new TestCompiler();

        DefinitionBootstrap bootstrap = new DefinitionBootstrap(compiler);

        DefinitionRegistry definitions = bootstrap.load(directory);

        DefinitionId id = definitions.resolve("core:test");

        assertEquals(DefinitionId.of(0), id);
        assertEquals(id, compiler.definitionId);
        assertEquals(42, compiler.value);
        assertTrue(definitions.isFrozen());
    }

    @Test
    void rejectsNullCompilers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DefinitionBootstrap(
                        (DefinitionAspectCompiler[]) null));
    }

    private static final class TestCompiler
            implements DefinitionAspectCompiler {

        private DefinitionId definitionId;
        private int value;

        @Override
        public String key() {
            return "test-aspect";
        }

        @Override
        public void compile(
                DefinitionId definitionId,
                JsonObject data,
                DefinitionCatalog catalog) {
            this.definitionId = definitionId;
            value = data.get("value").getAsInt();
        }
    }

    @Test
    void rejectsSecondLoad()
            throws IOException {

        Files.writeString(
                directory.resolve("test.json"),
                """
                        {
                            "key": "core:test",
                            "aspects": {
                                "test-aspect": {
                                    "value": 42
                                }
                            }
                        }
                        """,
                UTF_8);

        DefinitionBootstrap bootstrap = new DefinitionBootstrap(
                new TestCompiler());

        bootstrap.load(directory);

        assertThrows(
                IllegalStateException.class,
                () -> bootstrap.load(directory));
    }

    @Test
    void rejectsRetryAfterFailedLoad()
            throws IOException {

        Files.writeString(
                directory.resolve("test.json"),
                """
                        {
                            "key": "core:test",
                            "aspects": {
                                "unknown": {}
                            }
                        }
                        """,
                UTF_8);

        DefinitionBootstrap bootstrap = new DefinitionBootstrap(
                new TestCompiler());

        assertThrows(
                IllegalArgumentException.class,
                () -> bootstrap.load(directory));

        assertThrows(
                IllegalStateException.class,
                () -> bootstrap.load(directory));
    }
}