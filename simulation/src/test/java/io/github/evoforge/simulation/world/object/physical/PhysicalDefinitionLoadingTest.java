package io.github.evoforge.simulation.world.object.physical;

import com.google.gson.JsonObject;
import io.github.evoforge.simulation.definition.DefinitionCompilerRegistry;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.definition.DefinitionLoader;
import io.github.evoforge.simulation.definition.DefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalDefinitionLoadingTest {

        @Test
        void loadsPhysicalDefinition() {
                DefinitionRegistry<ObjectDefinitionId> definitions = new DefinitionRegistry<>(ObjectDefinitionId::of, ObjectDefinitionId::asInt);

                DefinitionCompilerRegistry<ObjectDefinitionId> compilers = new DefinitionCompilerRegistry<>();

                PhysicalDefinitions physical = new PhysicalDefinitions();

                compilers.register(
                                new PhysicalDefinitionCompiler(
                                                physical));

                DefinitionLoader<ObjectDefinitionId> loader = new DefinitionLoader<>(
                                definitions,
                                compilers);

                JsonObject document = new JsonObject();

                document.addProperty(
                                "key",
                                "core:apple");

                JsonObject aspects = new JsonObject();

                JsonObject physicalAspect = new JsonObject();

                physicalAspect.addProperty(
                                "mass",
                                0.18);

                aspects.add(
                                "physical",
                                physicalAspect);

                document.add(
                                "aspects",
                                aspects);

                loader.load(
                                List.of(document));

                ObjectDefinitionId id = definitions.resolve(
                                "core:apple");

                assertEquals(
                                ObjectDefinitionId.of(0),
                                id);

                assertTrue(
                                physical.has(id));

                assertEquals(
                                0.18,
                                physical.mass(id));

                assertTrue(
                                physical.isFrozen());

                assertThrows(
                                IllegalStateException.class,
                                () -> physical.put(
                                                ObjectDefinitionId.of(10),
                                                1.0));
        }
}
