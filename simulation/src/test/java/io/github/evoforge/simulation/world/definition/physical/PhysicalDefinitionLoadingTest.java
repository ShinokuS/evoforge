package io.github.evoforge.simulation.world.definition.physical;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.evoforge.simulation.world.definition.DefinitionCompilerRegistry;
import io.github.evoforge.simulation.world.definition.DefinitionId;
import io.github.evoforge.simulation.world.definition.DefinitionLoader;
import io.github.evoforge.simulation.world.definition.DefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalDefinitionLoadingTest {

    @Test
    void loadsPhysicalAspect() {
        DefinitionRegistry definitions = new DefinitionRegistry();

        DefinitionCompilerRegistry compilers = new DefinitionCompilerRegistry();

        PhysicalDefinitions physical = new PhysicalDefinitions();

        compilers.register(
                new PhysicalDefinitionCompiler(physical));

        DefinitionLoader loader = new DefinitionLoader(definitions, compilers);

        JsonObject document = JsonParser
                .parseString("""
                        {
                            "key": "core:apple",
                            "aspects": {
                                "physical": {
                                    "mass": 0.18
                                }
                            }
                        }
                        """)
                .getAsJsonObject();

        loader.load(List.of(document));

        DefinitionId apple = definitions.resolve("core:apple");

        assertEquals(DefinitionId.of(0), apple);
        assertTrue(physical.has(apple));
        assertEquals(0.18, physical.mass(apple));
    }
}