package io.github.evoforge.simulation.world.geology;

import java.util.List;

/** Core composition defaults; custom content can compile and inject another profile. */
public final class GeologyProfiles {
    public static final String TEMPERATE_CRUST = "core:temperate_crust";
    public static final GeologyUnitKey GRANITE = GeologyUnitKey.of("core:granite");
    public static final GeologyUnitKey BASALT = GeologyUnitKey.of("core:basalt");
    public static final GeologyUnitKey LIMESTONE = GeologyUnitKey.of("core:limestone");
    public static final GeologyUnitKey SHALE = GeologyUnitKey.of("core:shale");

    private GeologyProfiles() { }

    public static CompiledGeologyProfile temperateCrust() {
        return new GeologyProfileCompiler().compile(new GeologyProfileDefinition(
                TEMPERATE_CRUST,
                List.of(
                        unit(GRANITE),
                        unit(BASALT),
                        unit(LIMESTONE),
                        unit(SHALE))));
    }

    private static GeologyProfileDefinition.UnitDefinition unit(GeologyUnitKey key) {
        return new GeologyProfileDefinition.UnitDefinition(
                key,
                GeologyMaterialKey.of(key.value()));
    }
}
