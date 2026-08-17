package io.github.evoforge.simulation.world.calibration.soil;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MaterialSoilHydraulicProfileFieldTest {
    @Test
    void materialProjectionHasNoCoordinateNoise() {
        WorldBounds bounds=new WorldBounds(0,2,0,0,0,0);
        TerrainMaterialKey soil=TerrainMaterialKey.of("test:soil");
        TerrainMaterialKey stone=TerrainMaterialKey.of("test:stone");
        SoilHydraulicProfile profile=SoilHydraulicProfile.ofPercent(45,30,12,WaterDepthRate.ZERO);
        TerrainMaterialField materials=new TerrainMaterialField(){
            public WorldBounds bounds(){return bounds;}
            public TerrainMaterialKey materialAt(int x,int y,int z){return x<2?soil:stone;}
        };
        SoilHydraulicProfileField field=new MaterialSoilHydraulicProfileField(
                materials,SoilHydraulicProfileBindings.of(Map.of(soil,profile)));
        assertSame(profile,field.find(0,0,0));
        assertSame(profile,field.find(1,0,0));
        assertNull(field.find(2,0,0));
    }
}
