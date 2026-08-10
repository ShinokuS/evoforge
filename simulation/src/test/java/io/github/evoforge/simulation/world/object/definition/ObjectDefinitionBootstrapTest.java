package io.github.evoforge.simulation.world.object.definition;

import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.definition.DefinitionRegistry;

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

class ObjectDefinitionBootstrapTest {

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

        ObjectDefinitionBootstrap bootstrap = new ObjectDefinitionBootstrap(compiler);

        DefinitionRegistry<ObjectDefinitionId> definitions = bootstrap.load(directory);

        ObjectDefinitionId id = definitions.resolve("core:test");

        assertEquals(ObjectDefinitionId.of(0), id);
        assertEquals(id, compiler.definitionId);
        assertEquals(42, compiler.value);
        assertTrue(definitions.isFrozen());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsNullCompilers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObjectDefinitionBootstrap(
                        (DefinitionAspectCompiler<ObjectDefinitionId>[]) null));
    }

    private static final class TestCompiler
            implements DefinitionAspectCompiler<ObjectDefinitionId> {

        private ObjectDefinitionId definitionId;
        private int value;

        @Override
        public String key() {
            return "test-aspect";
        }

        @Override
        public void compile(
                ObjectDefinitionId definitionId,
                JsonObject data,
                DefinitionCatalog<ObjectDefinitionId> catalog) {
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

        ObjectDefinitionBootstrap bootstrap = new ObjectDefinitionBootstrap(
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

        ObjectDefinitionBootstrap bootstrap = new ObjectDefinitionBootstrap(
                new TestCompiler());

        assertThrows(
                IllegalArgumentException.class,
                () -> bootstrap.load(directory));

        assertThrows(
                IllegalStateException.class,
                () -> bootstrap.load(directory));
    }
}
