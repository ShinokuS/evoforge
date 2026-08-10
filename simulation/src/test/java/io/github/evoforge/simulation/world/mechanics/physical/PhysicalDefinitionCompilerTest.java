package io.github.evoforge.simulation.world.mechanics.physical;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.evoforge.simulation.definition.DefinitionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalDefinitionCompilerTest {

        @Test
        void hasPhysicalKey() {
                PhysicalDefinitionCompiler compiler = new PhysicalDefinitionCompiler(
                                new PhysicalDefinitions());

                assertEquals("physical", compiler.key());
        }

        @Test
        void compilesMass() {
                PhysicalDefinitions definitions = new PhysicalDefinitions();

                PhysicalDefinitionCompiler compiler = new PhysicalDefinitionCompiler(definitions);

                DefinitionId id = DefinitionId.of(3);

                JsonObject data = parse("""
                                {
                                    "mass": 0.18
                                }
                                """);

                compiler.compile(id, data, null);

                assertTrue(definitions.has(id));
                assertEquals(0.18, definitions.mass(id));
        }

        @Test
        void rejectsMissingMass() {
                PhysicalDefinitionCompiler compiler = new PhysicalDefinitionCompiler(
                                new PhysicalDefinitions());

                JsonObject data = parse("""
                                {}
                                """);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> compiler.compile(
                                                DefinitionId.of(0),
                                                data,
                                                null));
        }

        @Test
        void rejectsNonNumericMass() {
                PhysicalDefinitionCompiler compiler = new PhysicalDefinitionCompiler(
                                new PhysicalDefinitions());

                JsonObject data = parse("""
                                {
                                    "mass": "heavy"
                                }
                                """);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> compiler.compile(
                                                DefinitionId.of(0),
                                                data,
                                                null));
        }

        @Test
        void rejectsInvalidMass() {
                PhysicalDefinitionCompiler compiler = new PhysicalDefinitionCompiler(
                                new PhysicalDefinitions());

                JsonObject data = parse("""
                                {
                                    "mass": 0
                                }
                                """);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> compiler.compile(
                                                DefinitionId.of(0),
                                                data,
                                                null));
        }

        @Test
        void rejectsNullData() {
                PhysicalDefinitionCompiler compiler = new PhysicalDefinitionCompiler(
                                new PhysicalDefinitions());

                assertThrows(
                                IllegalArgumentException.class,
                                () -> compiler.compile(
                                                DefinitionId.of(0),
                                                null,
                                                null));
        }

        @Test
        void rejectsNullDefinitions() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PhysicalDefinitionCompiler(null));
        }

        private static JsonObject parse(String json) {
                return JsonParser
                                .parseString(json)
                                .getAsJsonObject();
        }
}