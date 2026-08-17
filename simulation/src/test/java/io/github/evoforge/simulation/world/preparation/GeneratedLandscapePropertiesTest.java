package io.github.evoforge.simulation.world.preparation;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfile;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileField;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class GeneratedLandscapePropertiesTest {
    @Test
    void spatialFieldCanCarryLocalPhysicalDifferences() {
        WorldBounds bounds=new WorldBounds(-1,1,0,0,0,0);
        SoilHydraulicProfile coarse=SoilHydraulicProfile.ofPercent(45,30,12,WaterDepthRate.ZERO);
        SoilHydraulicProfile fine=SoilHydraulicProfile.ofPercent(50,36,18,WaterDepthRate.ZERO);
        SoilHydraulicProfileField field=new SoilHydraulicProfileField(){
            public WorldBounds bounds(){return bounds;}
            public SoilHydraulicProfile find(int x,int y,int z){return x<0?coarse:x>0?fine:null;}
        };
        GeneratedLandscapeProperties properties=new GeneratedLandscapeProperties(field);
        assertSame(coarse,properties.soilHydraulics().find(-1,0,0));
        assertNull(properties.soilHydraulics().find(0,0,0));
        assertSame(fine,properties.soilHydraulics().find(1,0,0));
    }

    @Test
    void emptyPropertiesAreBoundedAndExplicitlyEmpty() {
        GeneratedLandscapeProperties properties=GeneratedLandscapeProperties.empty(
                new WorldBounds(0,0,0,0,0,0));
        assertNull(properties.soilHydraulics().find(0,0,0));
        assertThrows(IllegalArgumentException.class,
                () -> properties.soilHydraulics().find(1,0,0));
    }
}
