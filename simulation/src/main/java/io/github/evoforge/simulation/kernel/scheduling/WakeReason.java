package io.github.evoforge.simulation.kernel.scheduling;

/** Human-readable reason why a sleeping process should become active again. */
public record WakeReason(String code) {
    public WakeReason {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }

    public static WakeReason scheduled() {
        return new WakeReason("scheduled");
    }

    public static WakeReason externalChange() {
        return new WakeReason("external-change");
    }
}
