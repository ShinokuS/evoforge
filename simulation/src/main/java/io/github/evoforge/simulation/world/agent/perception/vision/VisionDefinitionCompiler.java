package io.github.evoforge.simulation.world.agent.perception.vision;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.math.BigDecimal;

/** Compiles the independent object-definition `vision` aspect. */
public final class VisionDefinitionCompiler implements DefinitionAspectCompiler<ObjectDefinitionId> {
    private final VisionDefinitions definitions;

    public VisionDefinitionCompiler(VisionDefinitions definitions) {
        if (definitions == null) throw new IllegalArgumentException("definitions must not be null");
        this.definitions = definitions;
    }

    @Override public String key() { return "vision"; }

    @Override
    public void compile(ObjectDefinitionId definitionId, JsonObject data, DefinitionCatalog<ObjectDefinitionId> catalog) {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        definitions.put(definitionId, new VisionDefinition(
                integer(data.get("range"), "vision.range"),
                integer(data.get("horizontalFovDegrees"), "vision.horizontalFovDegrees")));
    }

    @Override public void finish() { definitions.freeze(); }

    private static int integer(JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(path + " is required");
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) throw new IllegalArgumentException(path + " must be an integer");
        try {
            return new BigDecimal(primitive.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(path + " must be an integer", exception);
        }
    }
}
