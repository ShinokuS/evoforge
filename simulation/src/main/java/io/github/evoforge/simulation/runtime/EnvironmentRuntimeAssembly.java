package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.time.BoundProcessScheduler;
import io.github.evoforge.simulation.time.HandlerId;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.world.environment.atmosphere.AtmosphericWaterForcingProcess;
import io.github.evoforge.simulation.world.environment.atmosphere.AtmosphericWaterForcingResult;
import io.github.evoforge.simulation.world.environment.atmosphere.AtmosphericWaterForcingSystem;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationSystem;
import io.github.evoforge.simulation.world.environment.evaporation.PeriodicEvaporationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.PeriodicPrecipitationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationEventLookup;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.SkyPrecipitationSystem;
import io.github.evoforge.simulation.world.environment.sky.VerticalSkySurfaceSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowProcess;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSurfaceRetentionLookup;
import io.github.evoforge.simulation.world.landscape.liquid.TerrainSurfaceRetentionLookup;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidInfiltrationSystem;

/** Builds and schedules atmosphere, free-liquid flow and soil infiltration runtime processes. */
final class EnvironmentRuntimeAssembly {
    private EnvironmentRuntimeAssembly() { }

    static EnvironmentRuntime assemble(
            SimulationDefinitions definitions,
            SimulationWorldState world,
            SimulationStartupConfig config,
            RuntimeKernel kernel) {
        LiquidSurfaceRetentionLookup surfaceRetention = new TerrainSurfaceRetentionLookup(
                world.landscape.terrain(), definitions.surfaceRetention);
        LiquidFlowSystem liquidFlow = new LiquidFlowSystem(
                world.liquids, world.geometry, surfaceRetention, definitions.liquidTransport);
        SoilLiquidInfiltrationSystem infiltration = new SoilLiquidInfiltrationSystem(
                world.liquids, world.landscape.terrain(), world.soilLiquids);
        LiquidFlowProcess liquidFlowProcess = new LiquidFlowProcess(liquidFlow, infiltration::update);
        HandlerId liquidFlowHandlerId = kernel.handlers.register(liquidFlowProcess::resume);
        ProcessScheduler liquidFlowScheduler = new BoundProcessScheduler(
                kernel.clock, kernel.scheduler, liquidFlowHandlerId);
        liquidFlowProcess.bindScheduler(liquidFlowScheduler);
        liquidFlowProcess.activate();

        VerticalSkySurfaceSystem skySurfaces = new VerticalSkySurfaceSystem(
                world.landscape.terrainSurfaces(), world.water.surfaces());
        PrecipitationEventLookup precipitationEvents = tick -> false;

        if (config.precipitation() != null) {
            PrecipitationSystem precipitation = new PrecipitationSystem(
                    world.landscape.terrain(), world.geometry, world.soilLiquids, world.water);
            SkyPrecipitationSystem skyPrecipitation = new SkyPrecipitationSystem(skySurfaces, precipitation);
            PeriodicPrecipitationSystem periodicPrecipitation = new PeriodicPrecipitationSystem(
                    skyPrecipitation, config.precipitation(), kernel.clock);
            precipitationEvents = periodicPrecipitation;
            HandlerId precipitationHandlerId = kernel.handlers.register(processId -> {
                periodicPrecipitation.resume(processId);
                liquidFlowProcess.activate();
            });
            ProcessScheduler precipitationScheduler = new BoundProcessScheduler(
                    kernel.clock, kernel.scheduler, precipitationHandlerId);
            periodicPrecipitation.bindScheduler(precipitationScheduler);
            periodicPrecipitation.start();
        }

        if (config.evaporation() != null) {
            EvaporationSystem evaporation = new EvaporationSystem(
                    skySurfaces,
                    world.water.surfaces(),
                    world.soilLiquids.cells(),
                    world.geometry,
                    world.water,
                    world.soilLiquids);
            PeriodicEvaporationSystem periodicEvaporation = new PeriodicEvaporationSystem(
                    evaporation, config.evaporation(), kernel.clock, precipitationEvents);
            HandlerId evaporationHandlerId = kernel.handlers.register(processId -> {
                periodicEvaporation.resume(processId);
                if (periodicEvaporation.lastResult().surfaceWaterRemoved() > 0L) {
                    liquidFlowProcess.activate();
                }
            });
            ProcessScheduler evaporationScheduler = new BoundProcessScheduler(
                    kernel.clock, kernel.scheduler, evaporationHandlerId);
            periodicEvaporation.bindScheduler(evaporationScheduler);
            periodicEvaporation.start();
        }

        if (config.atmosphericWaterForcing() != null) {
            PrecipitationSystem precipitation = new PrecipitationSystem(
                    world.landscape.terrain(), world.geometry, world.soilLiquids, world.water);
            SkyPrecipitationSystem skyPrecipitation = new SkyPrecipitationSystem(skySurfaces, precipitation);
            EvaporationSystem evaporation = new EvaporationSystem(
                    skySurfaces,
                    world.water.surfaces(),
                    world.soilLiquids.cells(),
                    world.geometry,
                    world.water,
                    world.soilLiquids);
            AtmosphericWaterForcingSystem forcing = new AtmosphericWaterForcingSystem(
                    config.atmosphericWaterForcing(), evaporation, skyPrecipitation);
            AtmosphericWaterForcingProcess forcingProcess = new AtmosphericWaterForcingProcess(
                    forcing, kernel.clock);
            HandlerId forcingHandlerId = kernel.handlers.register(processId -> {
                forcingProcess.resume(processId);
                AtmosphericWaterForcingResult result = forcingProcess.lastResult();
                if (result.precipitation().surfaceWater() > 0L
                        || result.evaporation().surfaceWaterRemoved() > 0L) {
                    liquidFlowProcess.activate();
                }
            });
            ProcessScheduler forcingScheduler = new BoundProcessScheduler(
                    kernel.clock, kernel.scheduler, forcingHandlerId);
            forcingProcess.bindScheduler(forcingScheduler);
            forcingProcess.start();
        }

        return new EnvironmentRuntime(surfaceRetention, liquidFlow, liquidFlowProcess);
    }
}
