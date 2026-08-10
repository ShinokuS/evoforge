package io.github.evoforge.simulation.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

public final class DefinitionLoader<I> {

    private final DefinitionRegistry<I> definitions;
    private final DefinitionCompilerRegistry<I> compilers;

    public DefinitionLoader(
            DefinitionRegistry<I> definitions,
            DefinitionCompilerRegistry<I> compilers) {
        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        if (compilers == null) {
            throw new IllegalArgumentException(
                    "compilers must not be null");
        }

        this.definitions = definitions;
        this.compilers = compilers;
    }

    public void load(List<JsonObject> documents) {
        if (documents == null) {
            throw new IllegalArgumentException(
                    "documents must not be null");
        }

        registerDefinitions(documents);
        compileDefinitions(documents);
        compilers.finishAll();
    }

    private void registerDefinitions(List<JsonObject> documents) {
        for (JsonObject document : documents) {
            JsonElement keyElement = document.get("key");

            if (keyElement == null || !keyElement.isJsonPrimitive()) {
                throw new IllegalArgumentException(
                        "definition key is required");
            }

            definitions.register(keyElement.getAsString());
        }
    }

    private void compileDefinitions(List<JsonObject> documents) {
        for (JsonObject document : documents) {
            String key = document.get("key").getAsString();
            I definitionId = definitions.resolve(key);

            JsonElement aspectsElement = document.get("aspects");

            if (aspectsElement == null || !aspectsElement.isJsonObject()) {
                throw new IllegalArgumentException(
                        "definition aspects are required: " + key);
            }

            JsonObject aspects = aspectsElement.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : aspects.entrySet()) {

                compileAspect(
                        definitionId,
                        entry.getKey(),
                        entry.getValue());
            }
        }
    }

    private void compileAspect(
            I definitionId,
            String aspectKey,
            JsonElement data) {
        DefinitionAspectCompiler<I> compiler = compilers.get(aspectKey);

        if (compiler == null) {
            throw new IllegalArgumentException(
                    "unknown definition aspect: " + aspectKey);
        }

        if (!data.isJsonObject()) {
            throw new IllegalArgumentException(
                    "definition aspect must be an object: "
                            + aspectKey);
        }

        compiler.compile(
                definitionId,
                data.getAsJsonObject(),
                definitions);
    }
}
