package io.github.evoforge.simulation.definition;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DefinitionCompilerRegistry {

    private final Map<String, DefinitionAspectCompiler> compilers = new LinkedHashMap<>();

    public void register(DefinitionAspectCompiler compiler) {
        if (compiler == null) {
            throw new IllegalArgumentException(
                    "compiler must not be null");
        }

        String key = compiler.key();

        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "compiler key must not be blank");
        }

        if (compilers.containsKey(key)) {
            throw new IllegalArgumentException(
                    "compiler already registered: " + key);
        }

        compilers.put(key, compiler);
    }

    public DefinitionAspectCompiler get(String key) {
        if (key == null) {
            return null;
        }

        return compilers.get(key);
    }

    public boolean contains(String key) {
        if (key == null) {
            return false;
        }

        return compilers.containsKey(key);
    }

    public int size() {
        return compilers.size();
    }

    public void finishAll() {
        for (DefinitionAspectCompiler compiler : compilers.values()) {
            compiler.finish();
        }
    }
}
