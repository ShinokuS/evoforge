package io.github.evoforge.simulation.definition;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionCompilerRegistryTest {

    @Test
    void registersCompiler() {
        DefinitionCompilerRegistry<DefinitionId> registry = new DefinitionCompilerRegistry<>();

        DefinitionAspectCompiler<DefinitionId> compiler = new TestCompiler("physical");

        registry.register(compiler);

        assertEquals(1, registry.size());
        assertTrue(registry.contains("physical"));
    }

    @Test
    void returnsCompilerByKey() {
        DefinitionCompilerRegistry<DefinitionId> registry = new DefinitionCompilerRegistry<>();

        DefinitionAspectCompiler<DefinitionId> compiler = new TestCompiler("physical");

        registry.register(compiler);

        assertSame(compiler, registry.get("physical"));
    }

    @Test
    void supportsDifferentCompilers() {
        DefinitionCompilerRegistry<DefinitionId> registry = new DefinitionCompilerRegistry<>();

        DefinitionAspectCompiler<DefinitionId> physical = new TestCompiler("physical");

        DefinitionAspectCompiler<DefinitionId> decay = new TestCompiler("decay");

        registry.register(physical);
        registry.register(decay);

        assertSame(physical, registry.get("physical"));
        assertSame(decay, registry.get("decay"));
        assertEquals(2, registry.size());
    }

    @Test
    void rejectsDuplicateCompilerKey() {
        DefinitionCompilerRegistry<DefinitionId> registry = new DefinitionCompilerRegistry<>();

        registry.register(new TestCompiler("physical"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        new TestCompiler("physical")));

        assertEquals(1, registry.size());
    }

    @Test
    void rejectsNullCompiler() {
        DefinitionCompilerRegistry<DefinitionId> registry = new DefinitionCompilerRegistry<>();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(null));
    }

    @Test
    void rejectsNullCompilerKey() {
        DefinitionCompilerRegistry<DefinitionId> registry = new DefinitionCompilerRegistry<>();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        new TestCompiler(null)));
    }

    @Test
    void rejectsBlankCompilerKey() {
        DefinitionCompilerRegistry<DefinitionId> registry = new DefinitionCompilerRegistry<>();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        new TestCompiler("   ")));
    }

    @Test
    void returnsNullForUnknownCompiler() {
        DefinitionCompilerRegistry<DefinitionId> registry = new DefinitionCompilerRegistry<>();

        assertNull(registry.get("unknown"));
    }

    @Test
    void handlesNullLookup() {
        DefinitionCompilerRegistry<DefinitionId> registry = new DefinitionCompilerRegistry<>();

        assertNull(registry.get(null));
        assertFalse(registry.contains(null));
    }

    private static final class TestCompiler
            implements DefinitionAspectCompiler<DefinitionId> {

        private final String key;

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
                DefinitionCatalog<DefinitionId> catalog) {
        }
    }
}
