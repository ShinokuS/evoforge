package io.github.evoforge.simulation.agents.opportunity;

import io.github.evoforge.simulation.kernel.scheduling.ProcessScheduler;
import io.github.evoforge.simulation.world.object.ObjectId;
import java.util.HashMap;
import java.util.Map;

/**
 * Reusable ownership/scheduling shell for provider-owned opportunity use actions.
 *
 * <p>The lifecycle deliberately knows nothing about opportunity evaluation or domain effects. A
 * provider owns its active payload and completes it after {@link #resume(long)} returns that
 * payload. This keeps generic process bookkeeping out of each affordance implementation without
 * turning affordances into an inheritance hierarchy.</p>
 */
public final class OpportunityUseLifecycle<T> {
    private final String name;
    private final String exhaustedMessage;
    private final Map<ObjectId, T> activeByAgent = new HashMap<>();
    private final Map<Long, ScheduledUse<T>> activeByProcess = new HashMap<>();
    private final Map<ObjectId, OpportunityUseCompletion> lastCompletionByAgent = new HashMap<>();
    private ProcessScheduler scheduler;
    private long nextUseId;

    public OpportunityUseLifecycle(String name, String exhaustedMessage) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("opportunity use lifecycle name must not be blank");
        }
        if (exhaustedMessage == null || exhaustedMessage.isBlank()) {
            throw new IllegalArgumentException("opportunity use exhaustion message must not be blank");
        }
        this.name = name;
        this.exhaustedMessage = exhaustedMessage;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        if (this.scheduler != null) {
            throw new IllegalStateException(name + " scheduler is already bound");
        }
        this.scheduler = scheduler;
    }

    public OpportunityUseActionId nextActionId() {
        if (nextUseId == Long.MAX_VALUE) {
            throw new IllegalStateException(exhaustedMessage);
        }
        return new OpportunityUseActionId(nextUseId++);
    }

    public boolean isActive(ObjectId agentId) {
        return agentId != null && activeByAgent.containsKey(agentId);
    }

    public void schedule(
            ObjectId agentId,
            OpportunityUseActionId actionId,
            long delayTicks,
            T active) {
        if (agentId == null || actionId == null || active == null) {
            throw new IllegalArgumentException("scheduled opportunity use values must not be null");
        }
        if (delayTicks <= 0L) {
            throw new IllegalArgumentException("scheduled opportunity use delay must be > 0");
        }
        requireScheduler();
        if (activeByAgent.putIfAbsent(agentId, active) != null) {
            throw new IllegalStateException("opportunity use is already active for agent: " + agentId);
        }
        ScheduledUse<T> scheduled = new ScheduledUse<>(agentId, active);
        if (activeByProcess.putIfAbsent(actionId.value(), scheduled) != null) {
            activeByAgent.remove(agentId, active);
            throw new IllegalStateException("opportunity use process id is already active: " + actionId.value());
        }
        scheduler.scheduleAfter(delayTicks, actionId.value());
    }

    public T resume(long processId) {
        ScheduledUse<T> scheduled = activeByProcess.remove(processId);
        if (scheduled == null) {
            throw new IllegalStateException("unknown " + name + " use process: " + processId);
        }
        activeByAgent.remove(scheduled.agentId(), scheduled.active());
        return scheduled.active();
    }

    public void recordCompletion(ObjectId agentId, OpportunityUseCompletion completion) {
        if (agentId == null || completion == null) {
            throw new IllegalArgumentException("opportunity use completion values must not be null");
        }
        lastCompletionByAgent.put(agentId, completion);
    }

    public OpportunityUseCompletion lastCompletion(ObjectId agentId) {
        return agentId == null ? null : lastCompletionByAgent.get(agentId);
    }

    private void requireScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException(name + " scheduler is not bound");
        }
    }

    private record ScheduledUse<T>(ObjectId agentId, T active) { }
}
