package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.mechanics.hydrology.AtmosphericWaterForcing;
import io.github.evoforge.simulation.mechanics.hydrology.EvaporationSchedule;
import io.github.evoforge.simulation.mechanics.hydrology.PrecipitationSchedule;
import io.github.evoforge.simulation.world.space.measurement.PhysicalCellVolume;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.space.orientation.FacingDirection;
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
