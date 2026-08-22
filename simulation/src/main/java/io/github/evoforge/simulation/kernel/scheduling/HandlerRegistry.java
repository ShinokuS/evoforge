package io.github.evoforge.simulation.kernel.scheduling;

import java.util.ArrayList;
import java.util.List;

public final class HandlerRegistry {

    private final List<ScheduledHandler> handlers = new ArrayList<>();

    public HandlerId register(
            ScheduledHandler handler) {

        if (handler == null) {
            throw new IllegalArgumentException(
                    "handler must not be null");
        }

        HandlerId id = HandlerId.of(handlers.size());

        handlers.add(handler);

        return id;
    }

    public ScheduledHandler get(
            HandlerId id) {

        if (id == null) {
            return null;
        }

        int index = id.asInt();

        if (index >= handlers.size()) {
            return null;
        }

        return handlers.get(index);
    }

    public boolean contains(
            HandlerId id) {

        if (id == null) {
            return false;
        }

        return id.asInt() < handlers.size();
    }

    public int size() {
        return handlers.size();
    }
}