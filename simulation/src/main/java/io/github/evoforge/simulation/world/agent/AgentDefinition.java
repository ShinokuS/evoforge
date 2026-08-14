package io.github.evoforge.simulation.world.agent;

import java.util.Arrays;

/** Immutable definition data needed by the first autonomous-agent slice. */
public final class AgentDefinition {

    private final int perceptionRadius;
    private final CapabilityId[] capabilities;

    public AgentDefinition(int perceptionRadius, CapabilityId... capabilities) {
        if (perceptionRadius < 0) {
            throw new IllegalArgumentException("perceptionRadius must be >= 0");
        }
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities must not be null");
        }

        this.perceptionRadius = perceptionRadius;
        this.capabilities = capabilities.clone();
        Arrays.sort(this.capabilities);

        for (int index = 0; index < this.capabilities.length; index++) {
            CapabilityId capability = this.capabilities[index];
            if (capability == null) {
                throw new IllegalArgumentException("capability must not be null");
            }
            if (index > 0 && capability.equals(this.capabilities[index - 1])) {
                throw new IllegalArgumentException("duplicate capability: " + capability.value());
            }
        }
    }

    public int perceptionRadius() {
        return perceptionRadius;
    }

    public boolean hasCapability(CapabilityId capability) {
        if (capability == null) {
            return false;
        }
        return Arrays.binarySearch(capabilities, capability) >= 0;
    }

    public int capabilityCount() {
        return capabilities.length;
    }

    public CapabilityId capabilityAt(int index) {
        return capabilities[index];
    }
}
