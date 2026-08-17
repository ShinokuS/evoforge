package io.github.evoforge.visualizer.scenario.environment;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfile;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.weather.WeatherLookup;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.*;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Locale;

/** Compares two points on one continuous authored Soil scale under identical generated rain. */
public final class SoilHydraulicContrastScenario implements VisualizerScenario {
    static final WorldBounds BOUNDS=new WorldBounds(-8,8,-5,5,-4,4);

    @Override public String id(){return "soil-hydraulic-contrast";}
    @Override public String title(){return "Soil Hydraulic Contrast";}
    @Override public String description(){
        return "One generated rain event falls on coarse and fine points of a continuous Soil scale; exact hydraulics are derived before runtime.";
    }

    @Override
    public ScenarioSession create(){
        GeneratedWorldRuntime generated=SoilHydraulicContrastPreparation.start(BOUNDS);
        WeatherLookup weather=generated.weather().orElseThrow();
        WeatherPresentationLookup presentation=()->raining(weather)
                ? WeatherPresentation.rain(0.65f):WeatherPresentation.CLEAR;
        int focusZ=generated.atlas().elevation().elevationAt(0,0);
        return new ScenarioSession(generated.runtime(),new ScenarioView(focusZ,0f,0f,1f),
                diagnostics(generated.runtime(),weather),ObjectPresentationBindings.empty(),presentation);
    }

    private static ScenarioController diagnostics(SimulationRuntime runtime,WeatherLookup weather){
        String coarse=summary(SoilHydraulicContrastPreparation.COARSE);
        String fine=summary(SoilHydraulicContrastPreparation.FINE);
        return new ScenarioController(){
            private ScenarioDiagnostics current=ScenarioDiagnostics.NONE;
            @Override public void update(long tick){
                SideWater left=sideWater(runtime,BOUNDS.minX(),-1);
                SideWater right=sideWater(runtime,1,BOUNDS.maxX());
                current=new ScenarioDiagnostics(new ScenarioCellMarker[0],
                        "physicalTick=1h · left=fineness 0.10 "+coarse
                        +" · right=fineness 0.80 "+fine
                        +" · phase="+(raining(weather)?"RAIN":"DRY")
                        +" · left retained="+left.retained()+" free="+left.free()
                        +" · right retained="+right.retained()+" free="+right.free());
            }
            @Override public ScenarioDiagnostics diagnostics(){return current;}
        };
    }

    static SideWater sideWater(SimulationRuntime runtime,int minX,int maxX){
        long retained=0L,free=0L;
        for(int x=minX;x<=maxX;x++)for(int y=BOUNDS.minY();y<=BOUNDS.maxY();y++)
            for(int z=BOUNDS.minZ();z<=BOUNDS.maxZ();z++){
                retained+=runtime.view().soilLiquids().amountOf(WaterSystem.TYPE,x,y,z);
                free+=runtime.view().water().amount(x,y,z);
            }
        return new SideWater(retained,free);
    }

    private static String summary(SoilHydraulicProfile p){
        return String.format(Locale.ROOT,"porosity=%.2f%% FC=%.2f%% WP=%.2f%% Ks=%.3fmm/h",
                p.porosityPartsPerMillion()/10_000d,p.fieldCapacityPartsPerMillion()/10_000d,
                p.permanentWiltingPointPartsPerMillion()/10_000d,micrometersPerHour(p.saturatedHydraulicConductivity())/1_000d);
    }

    private static long micrometersPerHour(WaterDepthRate rate){
        BigInteger n=rate.depthNanometersNumerator().multiply(BigInteger.valueOf(Duration.ofHours(1).toNanos()));
        BigInteger d=rate.durationNanosecondsDenominator().multiply(BigInteger.valueOf(1_000L));
        return n.divide(d).longValueExact();
    }

    private static boolean raining(WeatherLookup weather){
        return weather.at(0,0).precipitationRate().depthNanometersNumerator().signum()>0;
    }

    record SideWater(long retained,long free){}
}
