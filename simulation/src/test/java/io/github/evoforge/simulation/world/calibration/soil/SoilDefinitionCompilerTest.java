package io.github.evoforge.simulation.world.calibration.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import org.junit.jupiter.api.Test;

final class SoilDefinitionCompilerTest {

    @Test
    void compilesOnlyNormalizedSemanticDefinitionData() {
        DefinitionRegistry<LandscapeDefinitionId> catalog = catalog();
        LandscapeDefinitionId id = catalog.register("test:soil");
        SoilDefinitionCompiler compiler = new SoilDefinitionCompiler();

        compiler.compile(
                id,
                parse("""
                        {
                          "mineralFineness": 0.4125,
                          "organicMatter": 0.8
                        }
                        """),
                catalog);

        assertThrows(IllegalStateException.class, compiler::bindings);
        compiler.finish();

        assertEquals(
                new SoilSemanticProfile(
                        NormalizedValue.ofPartsPerMillion(412_500),
                        NormalizedValue.ofPartsPerMillion(800_000)),
                compiler.bindings().require(TerrainMaterialKey.of("test:soil")));
    }

    @Test
    void rejectsPhysicalAndLegacyFieldsAtAuthoredBoundary() {
        DefinitionRegistry<LandscapeDefinitionId> catalog = catalog();
        LandscapeDefinitionId id = catalog.register("test:legacy");
        SoilDefinitionCompiler compiler = new SoilDefinitionCompiler();

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        id,
                        parse("""
                                {
                                  "mineralFineness": 0.4,
                                  "organicMatter": 0.4,
                                  "permeability": 60000
                                }
                                """),
                        catalog));
    }

    @Test
    void rejectsCoordinatesOutsideNormalizedInterval() {
        DefinitionRegistry<LandscapeDefinitionId> catalog = catalog();
        LandscapeDefinitionId id = catalog.register("test:invalid");
        SoilDefinitionCompiler compiler = new SoilDefinitionCompiler();

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        id,
                        parse("{\"mineralFineness\":1.01,\"organicMatter\":0.4}"),
                        catalog));
    }

    private static DefinitionRegistry<LandscapeDefinitionId> catalog() {
        return new DefinitionRegistry<>(LandscapeDefinitionId::of, LandscapeDefinitionId::asInt);
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
