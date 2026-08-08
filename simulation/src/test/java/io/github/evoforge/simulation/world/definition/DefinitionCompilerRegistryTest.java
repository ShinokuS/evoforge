package io.github.evoforge.simulation.world.definition;

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
        DefinitionCompilerRegistry registry = new DefinitionCompilerRegistry();

        DefinitionAspectCompiler compiler = new TestCompiler("physical");

        registry.register(compiler);

        assertEquals(1, registry.size());
        assertTrue(registry.contains("physical"));
    }

    @Test
    void returnsCompilerByKey() {
        DefinitionCompilerRegistry registry = new DefinitionCompilerRegistry();

        DefinitionAspectCompiler compiler = new TestCompiler("physical");

        registry.register(compiler);

        assertSame(compiler, registry.get("physical"));
    }

    @Test
    void supportsDifferentCompilers() {
        DefinitionCompilerRegistry registry = new DefinitionCompilerRegistry();

        DefinitionAspectCompiler physical = new TestCompiler("physical");

        DefinitionAspectCompiler decay = new TestCompiler("decay");

        registry.register(physical);
        registry.register(decay);

        assertSame(physical, registry.get("physical"));
        assertSame(decay, registry.get("decay"));
        assertEquals(2, registry.size());
    }

    @Test
    void rejectsDuplicateCompilerKey() {
        DefinitionCompilerRegistry registry = new DefinitionCompilerRegistry();

        registry.register(new TestCompiler("physical"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        new TestCompiler("physical")));

        assertEquals(1, registry.size());
    }

    @Test
    void rejectsNullCompiler() {
        DefinitionCompilerRegistry registry = new DefinitionCompilerRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(null));
    }

    @Test
    void rejectsNullCompilerKey() {
        DefinitionCompilerRegistry registry = new DefinitionCompilerRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        new TestCompiler(null)));
    }

    @Test
    void rejectsBlankCompilerKey() {
        DefinitionCompilerRegistry registry = new DefinitionCompilerRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        new TestCompiler("   ")));
    }

    @Test
    void returnsNullForUnknownCompiler() {
        DefinitionCompilerRegistry registry = new DefinitionCompilerRegistry();

        assertNull(registry.get("unknown"));
    }

    @Test
    void handlesNullLookup() {
        DefinitionCompilerRegistry registry = new DefinitionCompilerRegistry();

        assertNull(registry.get(null));
        assertFalse(registry.contains(null));
    }

    private static final class TestCompiler
            implements DefinitionAspectCompiler {

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
                DefinitionResolver resolver) {
        }
    }
}