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

class DefinitionDirectoryLoaderTest {

    @TempDir
    Path directory;

    @Test
    void loadsDefinitionsFromDirectory() throws IOException {
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

        DefinitionRegistry definitions = new DefinitionRegistry();

        DefinitionCompilerRegistry compilers = new DefinitionCompilerRegistry();

        TestCompiler compiler = new TestCompiler();

        compilers.register(compiler);

        DefinitionLoader loader = new DefinitionLoader(
                definitions,
                compilers);

        DefinitionDirectoryLoader directoryLoader = new DefinitionDirectoryLoader(
                new DefinitionFileReader(),
                loader);

        directoryLoader.load(directory);

        DefinitionId id = definitions.resolve("core:test");

        assertEquals(DefinitionId.of(0), id);
        assertEquals(42, compiler.value);
        assertEquals(id, compiler.definitionId);
    }

    @Test
    void rejectsNullReader() {
        DefinitionLoader loader = new DefinitionLoader(
                new DefinitionRegistry(),
                new DefinitionCompilerRegistry());

        assertThrows(
                IllegalArgumentException.class,
                () -> new DefinitionDirectoryLoader(
                        null,
                        loader));
    }

    @Test
    void rejectsNullLoader() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DefinitionDirectoryLoader(
                        new DefinitionFileReader(),
                        null));
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
}