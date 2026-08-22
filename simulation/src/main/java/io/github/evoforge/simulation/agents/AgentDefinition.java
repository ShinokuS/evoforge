package io.github.evoforge.simulation.agents;

import java.util.Arrays;

/** Immutable definition data for generic autonomous composition. */
public final class AgentDefinition {
    private final CapabilityId[] capabilities;

    public AgentDefinition(CapabilityId... capabilities) {
        if (capabilities == null) throw new IllegalArgumentException("capabilities must not be null");
        this.capabilities = capabilities.clone();
        Arrays.sort(this.capabilities);
        for (int index = 0; index < this.capabilities.length; index++) {
            CapabilityId capability = this.capabilities[index];
            if (capability == null) throw new IllegalArgumentException("capability must not be null");
            if (index > 0 && capability.equals(this.capabilities[index - 1])) {
                throw new IllegalArgumentException("duplicate capability: " + capability.value());
            }
        }
    }

    public boolean hasCapability(CapabilityId capability) {
        return capability != null && Arrays.binarySearch(capabilities, capability) >= 0;
    }
    public int capabilityCount() { return capabilities.length; }
    public CapabilityId capabilityAt(int index) { return capabilities[index]; }
}
