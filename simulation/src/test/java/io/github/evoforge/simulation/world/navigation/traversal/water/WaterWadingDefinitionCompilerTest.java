package io.github.evoforge.simulation.world.navigation.traversal.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

final class WaterWadingDefinitionCompilerTest {

    @Test
    void compilesNormalizedMaxDepth() {
        WaterWadingDefinitions definitions = new WaterWadingDefinitions();
        WaterWadingDefinitionCompiler compiler =
                new WaterWadingDefinitionCompiler(definitions);
        ObjectDefinitionId id = ObjectDefinitionId.of(4);

        compiler.compile(
                id,
                parse("""
                        {
                            "maxDepth": 275000
                        }
                        """),
                null);

        assertTrue(definitions.has(id));
        assertEquals(275_000, definitions.profile(id).maxDepth());
    }

    @Test
    void rejectsFractionalAndOutOfRangeDepth() {
        WaterWadingDefinitionCompiler compiler =
                new WaterWadingDefinitionCompiler(new WaterWadingDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        ObjectDefinitionId.of(0),
                        parse("""
                                {
                                    "maxDepth": 1.5
                                }
                                """),
                        null));

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        ObjectDefinitionId.of(1),
                        parse("""
                                {
                                    "maxDepth": 1000001
                                }
                                """),
                        null));
    }

    @Test
    void finishFreezesDefinitions() {
        WaterWadingDefinitions definitions = new WaterWadingDefinitions();
        WaterWadingDefinitionCompiler compiler =
                new WaterWadingDefinitionCompiler(definitions);

        compiler.finish();

        assertTrue(definitions.isFrozen());
        assertThrows(
                IllegalStateException.class,
                () -> definitions.put(
                        ObjectDefinitionId.of(0),
                        new WaterWadingProfile(0)));
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
