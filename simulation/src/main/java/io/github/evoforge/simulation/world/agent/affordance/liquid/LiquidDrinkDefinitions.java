package io.github.evoforge.simulation.world.agent.affordance.liquid;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Build-time mutable, runtime immutable drinking capabilities by agent definition. */
public final class LiquidDrinkDefinitions {
    private final Map<ObjectDefinitionId, List<LiquidDrinkDefinition>> byDefinition = new HashMap<>();
    private boolean frozen;

    public void add(ObjectDefinitionId definitionId, LiquidDrinkDefinition definition) {
        requireMutable();
        if (definitionId == null || definition == null) {
            throw new IllegalArgumentException("liquid drink definition values must not be null");
        }
        List<LiquidDrinkDefinition> values = byDefinition.computeIfAbsent(
                definitionId, ignored -> new ArrayList<>());
        for (LiquidDrinkDefinition existing : values) {
            if (existing.needId().equals(definition.needId())
                    && existing.liquidType().equals(definition.liquidType())) {
                throw new IllegalArgumentException(
                        "duplicate liquid drink definition for " + definitionId
                                + ": " + definition.needId() + " / " + definition.liquidType());
            }
        }
        values.add(definition);
    }

    public boolean has(ObjectDefinitionId definitionId) {
        List<LiquidDrinkDefinition> values = byDefinition.get(definitionId);
        return values != null && !values.isEmpty();
    }

    public int count(ObjectDefinitionId definitionId) {
        List<LiquidDrinkDefinition> values = byDefinition.get(definitionId);
        return values == null ? 0 : values.size();
    }

    public LiquidDrinkDefinition definitionAt(ObjectDefinitionId definitionId, int index) {
        List<LiquidDrinkDefinition> values = byDefinition.get(definitionId);
        if (values == null) throw new IllegalArgumentException("no liquid drink definitions: " + definitionId);
        return values.get(index);
    }

    public void freeze() {
        if (frozen) return;
        for (Map.Entry<ObjectDefinitionId, List<LiquidDrinkDefinition>> entry : byDefinition.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        frozen = true;
    }

    private void requireMutable() {
        if (frozen) throw new IllegalStateException("liquid drink definitions are frozen");
    }
}
