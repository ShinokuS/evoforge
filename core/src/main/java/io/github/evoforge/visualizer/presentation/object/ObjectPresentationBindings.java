package io.github.evoforge.visualizer.presentation.object;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.Map;

/** Immutable scenario/presentation-owned bindings from simulation definitions to visual metadata. */
public final class ObjectPresentationBindings {
    private static final ObjectPresentationBindings EMPTY = new ObjectPresentationBindings(Map.of());
    private final Map<ObjectDefinitionId, ObjectPresentation> values;

    public ObjectPresentationBindings(Map<ObjectDefinitionId, ObjectPresentation> values) {
        if (values == null) throw new IllegalArgumentException("values must not be null");
        for (Map.Entry<ObjectDefinitionId, ObjectPresentation> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("presentation bindings must not contain null values");
            }
        }
        this.values = Map.copyOf(values);
    }

    public static ObjectPresentationBindings empty() { return EMPTY; }

    public ObjectPresentation get(ObjectDefinitionId definitionId) {
        return definitionId == null ? null : values.get(definitionId);
    }
}
