package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ScenarioCatalogTest {

    @Test
    void standardCatalogKeepsFocusedScenarioOrder() {
        ScenarioCatalog catalog = ScenarioCatalog.standard();

        assertEquals(
                List.of(
                        "cutaway",
                        "ramp-navigation",
                        "timed-movement",
                        "occupancy-contention"),
                catalog.scenarios().stream()
                        .map(VisualizerScenario::id)
                        .toList());
    }

    @Test
    void creatingTheSameScenarioTwiceProducesFreshRuntime() {
        VisualizerScenario scenario = ScenarioCatalog.standard().get(2);

        ScenarioSession first = scenario.create();
        ScenarioSession second = scenario.create();

        assertNotSame(first.runtime(), second.runtime());
        assertEquals(0, first.runtime().time().tick());
        assertEquals(0, second.runtime().time().tick());
        assertEquals(first.view(), second.view());
    }

    @Test
    void duplicateIdsAreRejected() {
        VisualizerScenario duplicate = new VisualizerScenario() {
            @Override
            public String id() {
                return "same";
            }

            @Override
            public String title() {
                return "Scenario";
            }

            @Override
            public String description() {
                return "Description";
            }

            @Override
            public ScenarioSession create() {
                return new CutawayScenario().create();
            }
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> new ScenarioCatalog(List.of(duplicate, duplicate)));
    }
}
