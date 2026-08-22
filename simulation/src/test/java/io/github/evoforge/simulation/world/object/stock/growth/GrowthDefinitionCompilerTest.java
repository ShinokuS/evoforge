package io.github.evoforge.simulation.world.object.stock.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class GrowthDefinitionCompilerTest {

    @Test
    void compilesIndependentGrowthAspect() {
        GrowthDefinitions definitions = new GrowthDefinitions();
        GrowthDefinitionCompiler compiler = new GrowthDefinitionCompiler(definitions);
        ObjectDefinitionId id = ObjectDefinitionId.of(7);

        compiler.compile(
                id,
                JsonParser.parseString("{\"baseAmount\":3,\"intervalTicks\":12}").getAsJsonObject(),
                null);

        GrowthDefinition definition = definitions.get(id);
        assertEquals(3, definition.baseAmount());
        assertEquals(12, definition.intervalTicks());
    }

    @Test
    void finishFreezesGrowthDefinitions() {
        GrowthDefinitions definitions = new GrowthDefinitions();
        GrowthDefinitionCompiler compiler = new GrowthDefinitionCompiler(definitions);
        compiler.finish();

        assertTrue(definitions.isFrozen());
        assertThrows(
                IllegalStateException.class,
                () -> definitions.put(ObjectDefinitionId.of(0), new GrowthDefinition(1, 2)));
    }
}
