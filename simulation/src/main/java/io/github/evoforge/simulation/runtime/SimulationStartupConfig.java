package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.world.environment.atmosphere.AtmosphericWaterForcing;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationSchedule;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationSchedule;
import io.github.evoforge.simulation.world.mechanics.measurement.PhysicalCellVolume;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
import java.util.List;
import java.util.Map;

/** Immutable snapshot of pre-start configuration consumed exactly once by runtime assembly. */
record SimulationStartupConfig(
        PrecipitationSchedule precipitation,
        EvaporationSchedule evaporation,
        AtmosphericWaterForcing atmosphericWaterForcing,
        PhysicalCellVolume physicalCellVolume,
        List<ObjectId> createdObjects,
        Map<ObjectId, FacingDirection> initialFacing) {

    SimulationStartupConfig {
        createdObjects = List.copyOf(createdObjects);
        initialFacing = Map.copyOf(initialFacing);
    }
}
