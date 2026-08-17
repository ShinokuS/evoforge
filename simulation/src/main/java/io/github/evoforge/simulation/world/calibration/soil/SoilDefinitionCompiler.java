package io.github.evoforge.simulation.world.calibration.soil;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.evoforge.simulation.definition.DefinitionAspectCompiler;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Compiles the authored {@code soil} aspect only as semantic Definition data.
 *
 * <p>No physical composition, hydraulic property or runtime Soil value is produced here. Those
 * belong to generated-world preparation models that can combine this archetype with world and
 * local context.</p>
 */
public final class SoilDefinitionCompiler
        implements DefinitionAspectCompiler<LandscapeDefinitionId> {

    private static final Set<String> FIELDS = Set.of("mineralFineness", "organicMatter");

    private final Map<TerrainMaterialKey, SoilSemanticProfile> profiles = new LinkedHashMap<>();
    private boolean finished;

    @Override
    public String key() {
        return "soil";
    }

    @Override
    public void compile(
            LandscapeDefinitionId definitionId,
            JsonObject data,
            DefinitionCatalog<LandscapeDefinitionId> catalog) {
        if (finished) {
            throw new IllegalStateException("soil definition compiler is already finished");
        }
        if (definitionId == null || data == null || catalog == null) {
            throw new IllegalArgumentException("soil definition inputs must not be null");
        }
        rejectUnknownFields(data);

        SoilSemanticProfile semantic = new SoilSemanticProfile(
                normalized(data, "mineralFineness"),
                normalized(data, "organicMatter"));
        TerrainMaterialKey materialKey = TerrainMaterialKey.of(catalog.keyOf(definitionId));
        if (profiles.putIfAbsent(materialKey, semantic) != null) {
            throw new IllegalStateException("duplicate soil definition: " + materialKey);
        }
    }

    @Override
    public void finish() {
        finished = true;
    }

    public SoilSemanticProfileBindings bindings() {
        if (!finished) {
            throw new IllegalStateException("soil definitions are not finished");
        }
        return SoilSemanticProfileBindings.of(profiles);
    }

    private static NormalizedValue normalized(JsonObject data, String field) {
        JsonElement element = data.get(field);
        String qualified = "soil." + field;
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(qualified + " is required");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(qualified + " must be a normalized number");
        }
        try {
            return NormalizedValue.parse(primitive.getAsString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    qualified + " must be within 0..1 with at most six decimal places",
                    exception);
        }
    }

    private static void rejectUnknownFields(JsonObject data) {
        for (String field : data.keySet()) {
            if (!FIELDS.contains(field)) {
                throw new IllegalArgumentException("unknown soil field: " + field);
            }
        }
    }
}
