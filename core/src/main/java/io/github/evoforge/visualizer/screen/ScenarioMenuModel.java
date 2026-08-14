package io.github.evoforge.visualizer.screen;

import io.github.evoforge.visualizer.scenario.ScenarioCatalog;
import io.github.evoforge.visualizer.scenario.ScenarioGroup;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure selector state for the scenario browser; contains no libGDX state. */
final class ScenarioMenuModel {

    record Row(
            ScenarioGroup group,
            VisualizerScenario scenario) {

        boolean isGroup() {
            return scenario == null;
        }

        String key() {
            return isGroup()
                    ? "group:" + group.id()
                    : "scenario:" + scenario.id();
        }
    }

    private final ScenarioCatalog catalog;
    private final Set<String> expandedGroups = new HashSet<>();
    private List<Row> rows = List.of();
    private String query = "";
    private int selectedIndex;

    ScenarioMenuModel(ScenarioCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "catalog must not be null");
        }
        this.catalog = catalog;
        expandedGroups.add(catalog.groups().get(0).id());
        rebuild(null);
    }

    int rowCount() {
        return rows.size();
    }

    Row row(int index) {
        return rows.get(index);
    }

    List<Row> rows() {
        return rows;
    }

    int selectedIndex() {
        return selectedIndex;
    }

    Row selectedRow() {
        return rows.get(selectedIndex);
    }

    String query() {
        return query;
    }

    int scenarioCount() {
        int count = 0;
        for (Row row : rows) {
            if (!row.isGroup()) {
                count++;
            }
        }
        return count;
    }

    boolean searching() {
        return !query.isBlank();
    }

    boolean isExpanded(ScenarioGroup group) {
        return searching() || expandedGroups.contains(group.id());
    }

    void moveSelection(int delta) {
        if (rows.isEmpty()) {
            return;
        }
        selectedIndex = Math.floorMod(
                selectedIndex + delta,
                rows.size());
    }

    void select(int index) {
        if (index < 0 || index >= rows.size()) {
            return;
        }
        selectedIndex = index;
    }

    VisualizerScenario activateSelected() {
        Row selected = selectedRow();
        if (!selected.isGroup()) {
            return selected.scenario();
        }
        toggle(selected.group());
        return null;
    }

    void expandSelected() {
        Row selected = selectedRow();
        if (selected.isGroup() && !searching()) {
            setExpanded(selected.group(), true);
        }
    }

    void collapseSelectedOrParent() {
        if (searching()) {
            return;
        }
        Row selected = selectedRow();
        setExpanded(selected.group(), false);
    }

    void toggle(ScenarioGroup group) {
        if (group == null || searching()) {
            return;
        }
        setExpanded(group, !expandedGroups.contains(group.id()));
    }

    void appendQuery(char character) {
        if (Character.isISOControl(character)) {
            return;
        }
        setQuery(query + character);
    }

    void backspaceQuery() {
        if (query.isEmpty()) {
            return;
        }
        setQuery(query.substring(0, query.length() - 1));
    }

    void clearQuery() {
        setQuery("");
    }

    void setQuery(String value) {
        String next = value == null ? "" : value;
        if (next.equals(query)) {
            return;
        }
        String selectedKey = rows.isEmpty()
                ? null
                : selectedRow().key();
        query = next;
        rebuild(selectedKey);
    }

    private void setExpanded(
            ScenarioGroup group,
            boolean expanded) {

        String selectedKey = selectedRow().key();
        if (expanded) {
            expandedGroups.add(group.id());
        } else {
            expandedGroups.remove(group.id());
        }
        rebuild(selectedKey);
    }

    private void rebuild(String preferredKey) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Row> next = new ArrayList<>();

        for (ScenarioGroup group : catalog.groups()) {
            boolean groupMatches = matches(group.id(), needle)
                    || matches(group.title(), needle);
            List<VisualizerScenario> matches = new ArrayList<>();

            for (VisualizerScenario scenario : group.scenarios()) {
                if (needle.isEmpty()
                        || groupMatches
                        || matches(scenario.id(), needle)
                        || matches(scenario.title(), needle)
                        || matches(scenario.description(), needle)) {
                    matches.add(scenario);
                }
            }

            if (!needle.isEmpty() && matches.isEmpty()) {
                continue;
            }

            next.add(new Row(group, null));
            if (!needle.isEmpty() || expandedGroups.contains(group.id())) {
                for (VisualizerScenario scenario : matches) {
                    next.add(new Row(group, scenario));
                }
            }
        }

        if (next.isEmpty()) {
            selectedIndex = 0;
            rows = List.of();
            return;
        }

        rows = List.copyOf(next);
        selectedIndex = findRow(preferredKey);
    }

    private int findRow(String preferredKey) {
        if (preferredKey != null) {
            for (int index = 0; index < rows.size(); index++) {
                if (preferredKey.equals(rows.get(index).key())) {
                    return index;
                }
            }
        }
        return Math.min(selectedIndex, rows.size() - 1);
    }

    private static boolean matches(
            String value,
            String needle) {
        return !needle.isEmpty()
                && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
