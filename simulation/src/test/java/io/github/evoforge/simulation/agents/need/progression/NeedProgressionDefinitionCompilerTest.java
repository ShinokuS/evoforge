package io.github.evoforge.simulation.agents.need.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class NeedProgressionDefinitionCompilerTest {
    @Test
    void compilesOpenNeedIdsInStableKeyOrder() {
        NeedProgressionDefinitions definitions = new NeedProgressionDefinitions();
        NeedProgressionDefinitionCompiler compiler = new NeedProgressionDefinitionCompiler(definitions);
        ObjectDefinitionId id = ObjectDefinitionId.of(3);
        compiler.compile(id, parse("{\"core:thirst\":{\"baseAmount\":4,\"intervalTicks\":7},\"core:hunger\":{\"baseAmount\":2,\"intervalTicks\":5}}"), null);

        assertEquals(2, definitions.count(id));
        assertEquals(NeedId.of("core:hunger"), definitions.definitionAt(id, 0).needId());
        assertEquals(2, definitions.definitionAt(id, 0).baseAmount());
        assertEquals(5, definitions.definitionAt(id, 0).intervalTicks());
        assertEquals(NeedId.of("core:thirst"), definitions.definitionAt(id, 1).needId());
    }

    @Test
    void finishFreezesOnlyItsOwnDefinitionStore() {
        NeedProgressionDefinitions definitions = new NeedProgressionDefinitions();
        NeedProgressionDefinitionCompiler compiler = new NeedProgressionDefinitionCompiler(definitions);
        compiler.finish();
        assertTrue(definitions.isFrozen());
        assertThrows(IllegalStateException.class, () -> definitions.add(
                ObjectDefinitionId.of(0),
                new NeedProgressionDefinition(NeedId.of("test:deficit"), 1, 1)));
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
