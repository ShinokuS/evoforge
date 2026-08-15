package io.github.evoforge.simulation.world.landscape.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

final class SoilPropertiesDefinitionCompilerTest {

    @Test
    void hasSoilKey() {
        SoilPropertiesDefinitionCompiler compiler =
                new SoilPropertiesDefinitionCompiler(
                        new SoilPropertiesDefinitions());

        assertEquals("soil", compiler.key());
    }

    @Test
    void compilesCapacityAndPermeability() {
        SoilPropertiesDefinitions definitions =
                new SoilPropertiesDefinitions();
        SoilPropertiesDefinitionCompiler compiler =
                new SoilPropertiesDefinitionCompiler(definitions);
        LandscapeDefinitionId id = LandscapeDefinitionId.of(3);

        compiler.compile(
                id,
                parse("""
                        {
                            "capacity": 600000,
                            "permeability": 125000
                        }
                        """),
                null);

        assertTrue(definitions.has(id));
        assertEquals(
                new SoilProperties(600_000, 125_000),
                definitions.get(id));
    }

    @Test
    void finishFreezesDefinitions() {
        SoilPropertiesDefinitions definitions =
                new SoilPropertiesDefinitions();
        SoilPropertiesDefinitionCompiler compiler =
                new SoilPropertiesDefinitionCompiler(definitions);

        compiler.finish();

        assertTrue(definitions.isFrozen());
    }

    @Test
    void rejectsMissingPermeability() {
        SoilPropertiesDefinitionCompiler compiler =
                new SoilPropertiesDefinitionCompiler(
                        new SoilPropertiesDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        LandscapeDefinitionId.of(0),
                        parse("{\"capacity\": 500000}"),
                        null));
    }

    @Test
    void rejectsFractionalCapacity() {
        SoilPropertiesDefinitionCompiler compiler =
                new SoilPropertiesDefinitionCompiler(
                        new SoilPropertiesDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        LandscapeDefinitionId.of(0),
                        parse("""
                                {
                                    "capacity": 1.5,
                                    "permeability": 1
                                }
                                """),
                        null));
    }

    @Test
    void rejectsVolumeOutsideCellScale() {
        SoilPropertiesDefinitionCompiler compiler =
                new SoilPropertiesDefinitionCompiler(
                        new SoilPropertiesDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        LandscapeDefinitionId.of(0),
                        parse("""
                                {
                                    "capacity": 1000001,
                                    "permeability": 1
                                }
                                """),
                        null));
    }

    @Test
    void rejectsNullDefinitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SoilPropertiesDefinitionCompiler(null));
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
