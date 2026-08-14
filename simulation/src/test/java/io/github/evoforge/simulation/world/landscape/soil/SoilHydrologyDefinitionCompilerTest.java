package io.github.evoforge.simulation.world.landscape.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

final class SoilHydrologyDefinitionCompilerTest {

    @Test
    void hasSoilKey() {
        SoilHydrologyDefinitionCompiler compiler =
                new SoilHydrologyDefinitionCompiler(
                        new SoilHydrologyDefinitions());

        assertEquals("soil", compiler.key());
    }

    @Test
    void compilesFiniteHydrology() {
        SoilHydrologyDefinitions definitions =
                new SoilHydrologyDefinitions();
        SoilHydrologyDefinitionCompiler compiler =
                new SoilHydrologyDefinitionCompiler(definitions);
        LandscapeDefinitionId id = LandscapeDefinitionId.of(3);

        compiler.compile(
                id,
                parse("""
                        {
                            "capacity": 600000,
                            "infiltrationLimit": 125000
                        }
                        """),
                null);

        assertTrue(definitions.has(id));
        assertEquals(
                new SoilHydrology(600_000, 125_000),
                definitions.get(id));
    }

    @Test
    void finishFreezesDefinitions() {
        SoilHydrologyDefinitions definitions =
                new SoilHydrologyDefinitions();
        SoilHydrologyDefinitionCompiler compiler =
                new SoilHydrologyDefinitionCompiler(definitions);

        compiler.finish();

        assertTrue(definitions.isFrozen());
    }

    @Test
    void rejectsMissingInfiltrationLimit() {
        SoilHydrologyDefinitionCompiler compiler =
                new SoilHydrologyDefinitionCompiler(
                        new SoilHydrologyDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        LandscapeDefinitionId.of(0),
                        parse("{\"capacity\": 500000}"),
                        null));
    }

    @Test
    void rejectsFractionalCapacity() {
        SoilHydrologyDefinitionCompiler compiler =
                new SoilHydrologyDefinitionCompiler(
                        new SoilHydrologyDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        LandscapeDefinitionId.of(0),
                        parse("""
                                {
                                    "capacity": 1.5,
                                    "infiltrationLimit": 1
                                }
                                """),
                        null));
    }

    @Test
    void rejectsVolumeOutsideCellScale() {
        SoilHydrologyDefinitionCompiler compiler =
                new SoilHydrologyDefinitionCompiler(
                        new SoilHydrologyDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        LandscapeDefinitionId.of(0),
                        parse("""
                                {
                                    "capacity": 1000001,
                                    "infiltrationLimit": 1
                                }
                                """),
                        null));
    }

    @Test
    void rejectsNullDefinitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SoilHydrologyDefinitionCompiler(null));
    }

    private static JsonObject parse(
            String json) {

        return JsonParser
                .parseString(json)
                .getAsJsonObject();
    }
}
