package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.visualizer.scenario.ScenarioCatalog;
import io.github.evoforge.visualizer.scenario.ScenarioGroup;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ScenarioMenuModelTest {

    @Test
    void firstGroupStartsExpandedAndGroupsCollapseIndependently() {
        ScenarioMenuModel model = new ScenarioMenuModel(catalog());

        assertEquals(
                List.of("group:geometry", "scenario:cutaway", "scenario:ramp", "group:movement"),
                keys(model));

        model.select(0);
        model.activateSelected();
        assertEquals(
                List.of("group:geometry", "group:movement"),
                keys(model));

        model.select(1);
        model.activateSelected();
        assertEquals(
                List.of("group:geometry", "group:movement", "scenario:timed"),
                keys(model));
    }

    @Test
    void searchMatchesScenarioMetadataAcrossCollapsedGroups() {
        ScenarioMenuModel model = new ScenarioMenuModel(catalog());
        model.setQuery("speed");

        assertTrue(model.searching());
        assertEquals(
                List.of("group:movement", "scenario:timed"),
                keys(model));
        assertEquals(1, model.scenarioCount());
    }

    @Test
    void matchingAGroupNameShowsAllScenariosInThatGroup() {
        ScenarioMenuModel model = new ScenarioMenuModel(catalog());
        model.setQuery("geometry");

        assertEquals(
                List.of("group:geometry", "scenario:cutaway", "scenario:ramp"),
                keys(model));
    }

    @Test
    void clearingSearchRestoresPriorExpansionState() {
        ScenarioMenuModel model = new ScenarioMenuModel(catalog());
        model.select(3);
        model.activateSelected();
        model.setQuery("ramp");
        assertTrue(model.searching());

        model.clearQuery();

        assertFalse(model.searching());
        assertEquals(
                List.of(
                        "group:geometry",
                        "scenario:cutaway",
                        "scenario:ramp",
                        "group:movement",
                        "scenario:timed"),
                keys(model));
    }

    private static ScenarioCatalog catalog() {
        return ScenarioCatalog.ofGroups(
                ScenarioGroup.of(
                        "geometry",
                        "Geometry",
                        stub("cutaway", "Cutaway", "Cave cut"),
                        stub("ramp", "Ramp Navigation", "Vertical route")),
                ScenarioGroup.of(
                        "movement",
                        "Movement",
                        stub("timed", "Timed Movement", "Compare speed")));
    }

    private static List<String> keys(ScenarioMenuModel model) {
        return model.rows().stream()
                .map(ScenarioMenuModel.Row::key)
                .toList();
    }

    private static VisualizerScenario stub(
            String id,
            String title,
            String description) {
        return new VisualizerScenario() {
            @Override public String id() { return id; }
            @Override public String title() { return title; }
            @Override public String description() { return description; }
            @Override public ScenarioSession create() { throw new UnsupportedOperationException(); }
        };
    }
}
