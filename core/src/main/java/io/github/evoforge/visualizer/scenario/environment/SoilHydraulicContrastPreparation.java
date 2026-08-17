package io.github.evoforge.visualizer.scenario.environment;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.atlas.WorldGenerationAlgorithms;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntimeBootstrap;
import io.github.evoforge.simulation.world.calibration.rainfall.MeanPreservingRainfallRegimeCalibrator;
import io.github.evoforge.simulation.world.calibration.rainfall.RainfallOccurrenceField;
import io.github.evoforge.simulation.world.calibration.rainfall.RainfallOccurrenceNormal;
import io.github.evoforge.simulation.world.calibration.rainfall.RainfallRegimeField;
import io.github.evoforge.simulation.world.calibration.soil.ContinuousSoilCompositionCompiler;
import io.github.evoforge.simulation.world.calibration.soil.SaxtonRawls2006SoilHydraulicCalibrator;
import io.github.evoforge.simulation.world.calibration.soil.SoilCompositionCalibration;
import io.github.evoforge.simulation.world.calibration.soil.SoilCompositionCompiler;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicCalibrator;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileBindings;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicRuntimeBinder;
import io.github.evoforge.simulation.world.calibration.soil.SoilSemanticProfile;
import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.genesis.*;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.preparation.GeneratedWorldPreparation;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import io.github.evoforge.simulation.world.weather.model.AlternatingRainfallPulseAtmosphericPlan;
import java.time.Duration;
import java.util.Map;

final class SoilHydraulicContrastPreparation {
    static final SimulationTimeScale TIME = SimulationTimeScale.of(Duration.ofHours(1));
    static final SoilHydraulicProfile COARSE = profile(100_000,400_000);
    static final SoilHydraulicProfile FINE = profile(800_000,400_000);
    private static final PhysicalSpaceScale SPACE=PhysicalSpaceScale.cubicMillimeters(1_000L);
    private static final TerrainMaterialKey COARSE_KEY=TerrainMaterialKey.of("scenario:soil-coarse");
    private static final TerrainMaterialKey FINE_KEY=TerrainMaterialKey.of("scenario:soil-fine");
    private static final TerrainMaterialKey DIVIDER_KEY=TerrainMaterialKey.of("scenario:soil-divider");

    private SoilHydraulicContrastPreparation() {}

    static GeneratedWorldRuntime start(WorldBounds bounds) {
        WorldAtlas atlas=atlas(bounds);
        RainfallRegimeField rainfall=new MeanPreservingRainfallRegimeCalibrator().calibrate(
                atlas.climateNormals(),RainfallOccurrenceField.uniform(bounds,
                        new RainfallOccurrenceNormal(Duration.ofHours(11),Duration.ofHours(1))));
        SimulationAssembly assembly=SimulationAssembly.create();
        LandscapeDefinitionId coarse=assembly.landscapeDefinition("scenario:soil_coarse");
        LandscapeDefinitionId fine=assembly.landscapeDefinition("scenario:soil_fine");
        LandscapeDefinitionId divider=assembly.landscapeDefinition("scenario:soil_divider");
        assembly.surfaceRetention(coarse,400).surfaceRetention(fine,400).surfaceRetention(divider,400);
        TerrainMaterialBindings bindings=TerrainMaterialBindings.of(Map.of(
                COARSE_KEY,coarse,FINE_KEY,fine,DIVIDER_KEY,divider));
        SoilHydraulicRuntimeBinder.bind(assembly,bindings,
                SoilHydraulicProfileBindings.of(Map.of(COARSE_KEY,COARSE,FINE_KEY,FINE)),SPACE,TIME);
        TerrainMaterialResolver materials=(x,y,z)->bindings.resolve(x<0?COARSE_KEY:x>0?FINE_KEY:DIVIDER_KEY);
        return GeneratedWorldRuntimeBootstrap.withTimeScale(
                new AlternatingRainfallPulseAtmosphericPlan(rainfall,42L),TIME)
                .start(atlas,assembly,materials);
    }

    private static WorldAtlas atlas(WorldBounds bounds) {
        WaterDepthRate annualWater = WaterDepthRate.ofMillimeters(1_200L, Duration.ofDays(365L));
        ClimateSpec climate=ClimateSpec.physical(ClimateTemperature.ofMilliCelsius(12_000),250,
                annualWater,annualWater);
        WorldGenesis genesis=new WorldGenesis(new WorldSpec(bounds,climate,SPACE),4_204_212L,
                GenerationRevision.V8,RngRevision.V1);
        WorldGenerationAlgorithms algorithms=WorldGenerationAlgorithms.standard()
                .withSurfaceHydrology((g,e,d)->dry(bounds));
        return new GeneratedWorldPreparation(new WorldAtlasGenerator(algorithms)).generateFacts(genesis);
    }

    private static SurfaceHydrologyField dry(WorldBounds bounds) {
        return new SurfaceHydrologyField() {
            public WorldBounds bounds(){return bounds;}
            public int initialWaterVolumeAt(int x,int y){if(!contains(x,y))throw new IllegalArgumentException();return 0;}
            public boolean isShoreline(int x,int y){if(!contains(x,y))throw new IllegalArgumentException();return false;}
        };
    }

    private static SoilHydraulicProfile profile(int fineness,int organic) {
        SoilCompositionCompiler composition=new ContinuousSoilCompositionCompiler(SoilCompositionCalibration.representative());
        SoilHydraulicCalibrator hydraulics=new SaxtonRawls2006SoilHydraulicCalibrator();
        return hydraulics.calibrate(composition.compile(new SoilSemanticProfile(
                NormalizedValue.ofPartsPerMillion(fineness),NormalizedValue.ofPartsPerMillion(organic))));
    }
}
