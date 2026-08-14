package io.github.evoforge.simulation.world.landscape.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilMoistureStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

final class SoilMoistureCellsLookupTest {

    private static final LandscapeDefinitionId SOIL =
            LandscapeDefinitionId.of(0);

    @Test
    void tracksPositiveMoistureAndIteratesDeterministically() {
        SoilHydrologyDefinitions definitions = new SoilHydrologyDefinitions();
        definitions.put(
                SOIL,
                new SoilHydrology(CellVolume.FULL, CellVolume.FULL));
        definitions.freeze();

        SoilMoistureSystem moisture = new SoilMoistureSystem(
                new SparseSoilMoistureStorage(),
                (x, y, z) -> SOIL,
                definitions);

        moisture.infiltrateAtMost(5, 0, 2, 10);
        moisture.infiltrateAtMost(-1, 3, 7, 20);
        moisture.infiltrateAtMost(5, -2, 9, 30);

        assertEquals(3, moisture.cells().wetCellCount());

        List<String> cells = new ArrayList<>();
        moisture.cells().forEach((x, y, z) ->
                cells.add(x + ":" + y + ":" + z));
        assertEquals(
                List.of("-1:3:7", "5:-2:9", "5:0:2"),
                cells);

        moisture.removeAtMost(5, -2, 9, 30);
        assertEquals(2, moisture.cells().wetCellCount());
    }
}
