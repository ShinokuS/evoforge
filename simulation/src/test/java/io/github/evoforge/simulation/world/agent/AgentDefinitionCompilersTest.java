package io.github.evoforge.simulation.world.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.evoforge.simulation.world.agent.affordance.NeedSatisfactionDefinitionCompiler;
import io.github.evoforge.simulation.world.agent.affordance.NeedSatisfactionDefinitions;
import io.github.evoforge.simulation.world.agent.need.NeedDefinitionCompiler;
import io.github.evoforge.simulation.world.agent.need.NeedDefinitions;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.need.NeedSpec;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionDefinitionCompiler;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionDefinitions;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class AgentDefinitionCompilersTest {
    @Test void compilesAgentCapabilitiesWithoutOwningSensoryParameters() {
        AgentDefinitions definitions = new AgentDefinitions();
        AgentDefinitionCompiler compiler = new AgentDefinitionCompiler(definitions);
        ObjectDefinitionId id = ObjectDefinitionId.of(2);
        compiler.compile(id, parse("{\"capabilities\":[\"core:graze\",\"core:drink\"]}"), null);
        AgentDefinition definition = definitions.get(id);
        assertTrue(definition.hasCapability(CapabilityId.of("core:graze")));
        assertTrue(definition.hasCapability(CapabilityId.of("core:drink")));
    }

    @Test void compilesVisionAsIndependentDefinitionAspect() {
        VisionDefinitions definitions = new VisionDefinitions();
        VisionDefinitionCompiler compiler = new VisionDefinitionCompiler(definitions);
        ObjectDefinitionId id = ObjectDefinitionId.of(3);
        compiler.compile(id, parse("{\"range\":9,\"horizontalFovDegrees\":120}"), null);
        assertEquals(9, definitions.get(id).range());
        assertEquals(120, definitions.get(id).horizontalFovDegrees());
    }

    @Test void compilesOpenNeedIdsWithoutCentralCatalog() {
        NeedDefinitions definitions = new NeedDefinitions();
        NeedDefinitionCompiler compiler = new NeedDefinitionCompiler(definitions);
        ObjectDefinitionId id = ObjectDefinitionId.of(4);
        compiler.compile(id, parse("{\"core:thirst\":{\"max\":200,\"initial\":60},\"core:hunger\":{\"max\":100,\"initial\":80}}"), null);
        assertEquals(2, definitions.count(id));
        NeedSpec first = definitions.specAt(id, 0);
        assertEquals(NeedId.of("core:hunger"), first.id());
        assertEquals(100, first.maxLevel());
        assertEquals(80, first.initialLevel());
    }

    @Test void compilesNeedSatisfactionAsIndependentDefinitionAspect() {
        NeedSatisfactionDefinitions definitions = new NeedSatisfactionDefinitions();
        NeedSatisfactionDefinitionCompiler compiler = new NeedSatisfactionDefinitionCompiler(definitions);
        ObjectDefinitionId id = ObjectDefinitionId.of(5);
        compiler.compile(id, parse("{\"core:hunger\":{\"amount\":35,\"requiresCapability\":\"core:graze\"}}"), null);
        assertEquals(1, definitions.count(id));
        var satisfaction = definitions.satisfactionAt(id, 0);
        assertEquals(NeedId.of("core:hunger"), satisfaction.needId());
        assertEquals(35, satisfaction.amount());
        assertEquals(CapabilityId.of("core:graze"), satisfaction.requiredCapability());
    }

    @Test void compilerFinishFreezesItsOwnDefinitionStore() {
        NeedDefinitions definitions = new NeedDefinitions();
        NeedDefinitionCompiler compiler = new NeedDefinitionCompiler(definitions);
        compiler.finish();
        assertTrue(definitions.isFrozen());
        assertThrows(IllegalStateException.class, () -> definitions.add(ObjectDefinitionId.of(0),
                new NeedSpec(NeedId.of("core:test"), 10, 1)));
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
