package io.github.evoforge.simulation.world.definition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefinitionLoaderTest {

    @Test
    void registersDefinition() {
        DefinitionRegistry definitions = new DefinitionRegistry();

        DefinitionCompilerRegistry compilers = new DefinitionCompilerRegistry();

        DefinitionLoader loader = new DefinitionLoader(definitions, compilers);

        JsonObject document = parse("""
                {
                    "key": "core:test",
                    "aspects": {}
                }
                """);

        loader.load(List.of(document));

        assertEquals(
                DefinitionId.of(0),
                definitions.resolve("core:test"));
    }

    @Test
    void dispatchesAspectToRegisteredCompiler() {
        DefinitionRegistry definitions = new DefinitionRegistry();

        DefinitionCompilerRegistry compilers = new DefinitionCompilerRegistry();

        TestCompiler compiler = new TestCompiler("test-aspect");

        compilers.register(compiler);

        DefinitionLoader loader = new DefinitionLoader(definitions, compilers);

        JsonObject document = parse("""
                {
                    "key": "core:test",
                    "aspects": {
                        "test-aspect": {
                            "value": 42
                        }
                    }
                }
                """);

        loader.load(List.of(document));

        assertEquals(1, compiler.compileCount);
        assertEquals(DefinitionId.of(0), compiler.definitionId);
        assertEquals(42, compiler.data.get("value").getAsInt());
    }

    @Test
    void registersAllDefinitionsBeforeCompilingAspects() {
        DefinitionRegistry definitions = new DefinitionRegistry();

        DefinitionCompilerRegistry compilers = new DefinitionCompilerRegistry();

        ResolvingCompiler compiler = new ResolvingCompiler();

        compilers.register(compiler);

        DefinitionLoader loader = new DefinitionLoader(definitions, compilers);

        JsonObject first = parse("""
                {
                    "key": "core:first",
                    "aspects": {
                        "test-reference": {
                            "target": "core:second"
                        }
                    }
                }
                """);

        JsonObject second = parse("""
                {
                    "key": "core:second",
                    "aspects": {}
                }
                """);

        loader.load(List.of(first, second));

        assertNotNull(compiler.resolvedTarget);
        assertEquals(
                definitions.resolve("core:second"),
                compiler.resolvedTarget);
    }

    @Test
    void rejectsUnknownAspect() {
        DefinitionRegistry definitions = new DefinitionRegistry();

        DefinitionCompilerRegistry compilers = new DefinitionCompilerRegistry();

        DefinitionLoader loader = new DefinitionLoader(definitions, compilers);

        JsonObject document = parse("""
                {
                    "key": "core:test",
                    "aspects": {
                        "unknown": {}
                    }
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(List.of(document)));
    }

    private static JsonObject parse(String json) {
        return JsonParser
                .parseString(json)
                .getAsJsonObject();
    }

    private static final class TestCompiler
            implements DefinitionAspectCompiler {

        private final String key;

        private int compileCount;
        private DefinitionId definitionId;
        private JsonObject data;

        private TestCompiler(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public void compile(
                DefinitionId definitionId,
                JsonObject data,
                DefinitionCatalog catalog) {
            compileCount++;
            this.definitionId = definitionId;
            this.data = data;
        }
    }

    private static final class ResolvingCompiler
            implements DefinitionAspectCompiler {

        private DefinitionId resolvedTarget;

        @Override
        public String key() {
            return "test-reference";
        }

        @Override
        public void compile(
                DefinitionId definitionId,
                JsonObject data,
                DefinitionCatalog catalog) {
            resolvedTarget = catalog.resolve(
                    data.get("target").getAsString());
        }
    }
}