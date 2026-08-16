package io.github.evoforge.visualizer.interaction;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.pathfinding.PathSearchStatus;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerCommandSink;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerViewMode;
import io.github.evoforge.visualizer.presentation.portal.ViewPortal;
import io.github.evoforge.visualizer.presentation.portal.ViewPortalLookup;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;
import io.github.evoforge.visualizer.visual.SurfaceProjectionResolver;

/** Owns ordinary world interaction independently from rendering mode. */
public final class VisualizerInteractionController extends InputAdapter {

    private static final int PREVIEW_EXPANSIONS_PER_FRAME = 512;

    private final SimulationView view;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final SurfaceProjectionResolver surfaces;
    private final LandscapeSliceResolver slices;
    private final VisualizerContextMenu menu = new VisualizerContextMenu();
    private ViewPortalLookup portals = ViewPortalLookup.EMPTY;
    private VisualizerCommandSink commands = VisualizerCommandSink.NONE;
    private boolean manualMovementEnabled = true;

    private ObjectId menuObject;
    private ViewPortal menuPortal;
    private PathSearch previewSearch;
    private PreviewKey previewKey;
    private int viewportWidth = 1;
    private int viewportHeight = 1;

    public VisualizerInteractionController(
            SimulationView view,
            VisualizerState state,
            VisualizerCamera camera,
            SurfaceProjectionResolver surfaces,
            LandscapeSliceResolver slices) {
        if (view == null || state == null || camera == null || surfaces == null || slices == null) {
            throw new IllegalArgumentException("interaction dependencies must not be null");
        }
        this.view = view;
        this.state = state;
        this.camera = camera;
        this.surfaces = surfaces;
        this.slices = slices;
    }

    public void configure(ViewPortalLookup portals, VisualizerCommandSink commands) {
        if (portals == null || commands == null) {
            throw new IllegalArgumentException("interaction bindings must not be null");
        }
        this.portals = portals;
        this.commands = commands;
        closeMenu();
    }

    public void setManualMovementEnabled(boolean enabled) {
        manualMovementEnabled = enabled;
        if (enabled) return;
        closeMenu();
        if (state.moveTargeting()) state.cancelMoveTargeting();
        clearPreviewSearch();
        state.clearMoveTargetPreview();
    }

    public VisualizerContextMenu menu() { return menu; }
    public ViewPortalLookup portals() { return portals; }

    /** Advances bounded mover-aware advisory planning only while Move targeting is active. */
    public void update() {
        if (!manualMovementEnabled || !state.moveTargeting()) {
            clearPreviewSearch();
            state.clearMoveTargetPreview();
            return;
        }

        ObjectId mover = state.moveTargetingObject();
        if (mover == null || !view.transforms().has(mover)) {
            clearPreviewSearch();
            state.clearMoveTargetPreview();
            return;
        }

        int screenX = Gdx.input.getX();
        int screenY = Gdx.input.getY();
        VisualizerCamera.Cell hover = camera.cellAt(screenX, screenY);
        ViewPortal portal = portalAt(hover.x(), hover.y());

        // A Surface portal can annotate a doorway physically covered by terrain above.
        // The whole cell is therefore an interaction cell, not a hidden Movement target.
        // Inside, the doorway remains an ordinary visible target.
        if (portal != null && state.viewMode() == VisualizerViewMode.SURFACE) {
            clearPreviewSearch();
            state.clearMoveTargetPreview();
            return;
        }

        Target target = visibleTargetAt(hover.x(), hover.y());
        if (target == null) {
            clearPreviewSearch();
            state.setMoveTargetPreview(
                    hover.x(), hover.y(), selectionZAt(hover.x(), hover.y()),
                    VisualizerState.MoveTargetStatus.BLOCKED);
            return;
        }

        int sourceX = view.transforms().x(mover);
        int sourceY = view.transforms().y(mover);
        int sourceZ = view.transforms().z(mover);
        PreviewKey key = new PreviewKey(
                mover, sourceX, sourceY, sourceZ, target.x(), target.y(), target.z());

        if (!key.equals(previewKey)) {
            clearPreviewSearch();
            previewKey = key;
            if (sourceX == target.x() && sourceY == target.y() && sourceZ == target.z()) {
                state.setMoveTargetPreview(
                        target.x(), target.y(), target.z(), VisualizerState.MoveTargetStatus.REACHABLE);
                return;
            }
            previewSearch = view.moveTo().beginPreview(
                    mover,
                    target.x(),
                    target.y(),
                    target.z());
            if (previewSearch == null) {
                state.setMoveTargetPreview(
                        target.x(), target.y(), target.z(), VisualizerState.MoveTargetStatus.BLOCKED);
                return;
            }
            state.setMoveTargetPreview(
                    target.x(), target.y(), target.z(), VisualizerState.MoveTargetStatus.CHECKING);
        }

        advancePreview(target);
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewportWidth = width;
        viewportHeight = height;
        menu.close();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode != Input.Keys.ESCAPE) return false;
        return cancelOrBack();
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.RIGHT) {
            if (menu.visible()) {
                closeMenu();
                return true;
            }
            if (state.moveTargeting()) {
                state.cancelMoveTargeting();
                clearPreviewSearch();
                return true;
            }
            return false;
        }
        if (button != Input.Buttons.LEFT) return false;

        if (menu.visible()) {
            VisualizerContextMenu.Action action = menu.actionAt(screenX, screenY);
            if (action != null) {
                execute(action);
                return true;
            }
            closeMenu();
            return true;
        }

        VisualizerCamera.Cell cell = camera.cellAt(screenX, screenY);
        ViewPortal portal = portalAt(cell.x(), cell.y());
        ObjectId object = visibleObjectAt(cell.x(), cell.y());

        // LMB always inspects the world cell first. Objects and portals contribute
        // actions to that cell; their visual glyphs are not independent hit targets.
        inspectCell(cell, object);

        if (portal != null) {
            if (object != null && manualMovementEnabled) {
                openCombinedMenu(object, portal, screenX, screenY);
            } else {
                openPortalMenu(portal, screenX, screenY);
            }
            return true;
        }

        if (manualMovementEnabled && state.moveTargeting()) {
            Target target = visibleTargetAt(cell.x(), cell.y());
            if (target == null) {
                state.setInteractionMessage("Destination is not walkable");
                return true;
            }
            submitMoveTarget(target);
            return true;
        }

        if (object != null && manualMovementEnabled) {
            openObjectMenu(object, screenX, screenY);
        }
        return true;
    }

    /** Esc: menu -> unfinished target -> interior -> slice view -> hosting screen. */
    public boolean cancelOrBack() {
        if (menu.visible()) {
            closeMenu();
            return true;
        }
        if (state.moveTargeting()) {
            state.cancelMoveTargeting();
            clearPreviewSearch();
            return true;
        }
        if (state.viewMode() == VisualizerViewMode.INTERIOR) {
            state.leaveInterior();
            state.setInteractionMessage("");
            return true;
        }
        if (state.viewMode() == VisualizerViewMode.DEBUG_SLICE) {
            state.toggleDebugSlice();
            state.setInteractionMessage("");
            return true;
        }
        return false;
    }

    private void inspectCell(VisualizerCamera.Cell cell, ObjectId object) {
        if (object != null && !state.moveTargeting()) {
            state.selectCell(cell.x(), cell.y(), view.transforms().z(object), object);
            return;
        }
        state.selectCell(cell.x(), cell.y(), selectionZAt(cell.x(), cell.y()), null);
    }

    private void submitMoveTarget(Target target) {
        VisualizerState.MoveTargetPreview preview = state.moveTargetPreview();
        if (preview != null
                && preview.x() == target.x()
                && preview.y() == target.y()
                && preview.z() == target.z()
                && preview.status() == VisualizerState.MoveTargetStatus.BLOCKED) {
            state.selectCell(target.x(), target.y(), target.z(), null);
            state.setInteractionMessage("Destination is not reachable");
            return;
        }

        ObjectId mover = state.moveTargetingObject();
        if (mover == null) return;
        VisualizerCommandSink.CommandFeedback feedback = commands.moveTo(
                mover, target.x(), target.y(), target.z());
        if (feedback.accepted()) {
            state.selectCell(target.x(), target.y(), target.z(), null);
            state.finishMoveTargeting("");
            clearPreviewSearch();
        } else {
            state.setInteractionMessage(feedback.message());
        }
    }

    private void advancePreview(Target target) {
        if (previewSearch == null) return;
        PathSearchStatus status = previewSearch.status();
        if (status == PathSearchStatus.RUNNING) {
            status = previewSearch.advance(PREVIEW_EXPANSIONS_PER_FRAME);
        }
        switch (status) {
            case RUNNING -> state.setMoveTargetPreview(
                    target.x(), target.y(), target.z(), VisualizerState.MoveTargetStatus.CHECKING);
            case FOUND -> state.setMoveTargetPreview(
                    target.x(), target.y(), target.z(), VisualizerState.MoveTargetStatus.REACHABLE);
            case NO_PATH, STALE, CANCELLED -> state.setMoveTargetPreview(
                    target.x(), target.y(), target.z(), VisualizerState.MoveTargetStatus.BLOCKED);
        }
    }

    private void openObjectMenu(ObjectId objectId, int screenX, int screenY) {
        if (!manualMovementEnabled) return;
        menuObject = objectId;
        menuPortal = null;
        if (view.moveTo().isActive(objectId)) {
            menu.open(screenX, screenY, viewportWidth, viewportHeight,
                    "Object " + objectId,
                    VisualizerContextMenu.Action.MOVE,
                    VisualizerContextMenu.Action.CANCEL_MOVE);
        } else {
            menu.open(screenX, screenY, viewportWidth, viewportHeight,
                    "Object " + objectId,
                    VisualizerContextMenu.Action.MOVE);
        }
    }

    private void openPortalMenu(ViewPortal portal, int screenX, int screenY) {
        menuPortal = portal;
        menuObject = null;
        if (manualMovementEnabled && state.moveTargeting() && state.viewMode() == VisualizerViewMode.INTERIOR) {
            menu.open(
                    screenX,
                    screenY,
                    viewportWidth,
                    viewportHeight,
                    portal.label(),
                    VisualizerContextMenu.Action.MOVE_HERE,
                    VisualizerContextMenu.Action.RETURN_SURFACE);
            return;
        }
        menu.open(
                screenX,
                screenY,
                viewportWidth,
                viewportHeight,
                portal.label(),
                state.viewMode() == VisualizerViewMode.INTERIOR
                        ? VisualizerContextMenu.Action.RETURN_SURFACE
                        : VisualizerContextMenu.Action.ENTER);
    }

    private void openCombinedMenu(
            ObjectId object,
            ViewPortal portal,
            int screenX,
            int screenY) {
        if (!manualMovementEnabled) {
            openPortalMenu(portal, screenX, screenY);
            return;
        }
        menuObject = object;
        menuPortal = portal;
        boolean active = view.moveTo().isActive(object);
        String title = "Object " + object + " + " + portal.label();

        if (state.viewMode() == VisualizerViewMode.INTERIOR) {
            if (state.moveTargeting()) {
                if (active) {
                    menu.open(screenX, screenY, viewportWidth, viewportHeight, title,
                            VisualizerContextMenu.Action.MOVE,
                            VisualizerContextMenu.Action.CANCEL_MOVE,
                            VisualizerContextMenu.Action.MOVE_HERE,
                            VisualizerContextMenu.Action.RETURN_SURFACE);
                } else {
                    menu.open(screenX, screenY, viewportWidth, viewportHeight, title,
                            VisualizerContextMenu.Action.MOVE,
                            VisualizerContextMenu.Action.MOVE_HERE,
                            VisualizerContextMenu.Action.RETURN_SURFACE);
                }
            } else if (active) {
                menu.open(screenX, screenY, viewportWidth, viewportHeight, title,
                        VisualizerContextMenu.Action.MOVE,
                        VisualizerContextMenu.Action.CANCEL_MOVE,
                        VisualizerContextMenu.Action.RETURN_SURFACE);
            } else {
                menu.open(screenX, screenY, viewportWidth, viewportHeight, title,
                        VisualizerContextMenu.Action.MOVE,
                        VisualizerContextMenu.Action.RETURN_SURFACE);
            }
            return;
        }

        if (active) {
            menu.open(screenX, screenY, viewportWidth, viewportHeight, title,
                    VisualizerContextMenu.Action.MOVE,
                    VisualizerContextMenu.Action.CANCEL_MOVE,
                    VisualizerContextMenu.Action.ENTER);
        } else {
            menu.open(screenX, screenY, viewportWidth, viewportHeight, title,
                    VisualizerContextMenu.Action.MOVE,
                    VisualizerContextMenu.Action.ENTER);
        }
    }

    private void execute(VisualizerContextMenu.Action action) {
        switch (action) {
            case MOVE -> {
                ObjectId object = menuObject;
                closeMenu();
                if (manualMovementEnabled && object != null && view.objects().isAlive(object)) {
                    state.beginMoveTargeting(object);
                }
            }
            case MOVE_HERE -> {
                ViewPortal portal = menuPortal;
                closeMenu();
                if (manualMovementEnabled && portal != null && state.viewMode() == VisualizerViewMode.INTERIOR) {
                    Target target = visibleTargetAt(portal.interiorX(), portal.interiorY());
                    if (target == null) {
                        state.setInteractionMessage("Destination is not walkable");
                    } else {
                        submitMoveTarget(target);
                    }
                }
            }
            case CANCEL_MOVE -> {
                ObjectId object = menuObject;
                closeMenu();
                if (manualMovementEnabled && object != null) {
                    VisualizerCommandSink.CommandFeedback feedback = commands.cancelMove(object);
                    state.setInteractionMessage(feedback.accepted() ? "" : feedback.message());
                }
            }
            case ENTER -> {
                ViewPortal portal = menuPortal;
                ObjectId selected = state.selectedObject();
                closeMenu();
                if (portal != null) {
                    state.enterInterior(portal.interior());
                    state.selectCell(
                            portal.interiorX(), portal.interiorY(), portal.interiorZ(), selected);
                    camera.setView(portal.interiorX() + 0.5f, portal.interiorY() + 0.5f, 1f);
                    state.setInteractionMessage("");
                }
            }
            case RETURN_SURFACE -> {
                ViewPortal portal = menuPortal;
                ObjectId selected = state.selectedObject();
                closeMenu();
                state.leaveInterior();
                if (portal != null) {
                    state.selectCell(
                            portal.surfaceX(), portal.surfaceY(), portal.surfaceZ(), selected);
                    camera.setView(portal.surfaceX() + 0.5f, portal.surfaceY() + 0.5f, 1f);
                }
                state.setInteractionMessage("");
            }
        }
    }

    private ObjectId visibleObjectAt(int x, int y) {
        Target target = visibleTargetAt(x, y);
        if (target == null) return null;
        int count = view.cells().objectCount(x, y, target.z());
        if (count <= 0) return null;

        ObjectId selected = state.selectedObject();
        VisualizerState.CellSelection selectedCell = state.selectedCell();
        int selectedIndex = -1;
        if (selected != null && selectedCell != null
                && selectedCell.x() == x && selectedCell.y() == y && selectedCell.z() == target.z()) {
            for (int index = 0; index < count; index++) {
                if (selected.equals(view.cells().objectAt(x, y, target.z(), index))) {
                    selectedIndex = index;
                    break;
                }
            }
        }
        return view.cells().objectAt(x, y, target.z(), (selectedIndex + 1) % count);
    }

    private Target visibleTargetAt(int x, int y) {
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            int z = surfaces.standingZ(x, y);
            return z == SurfaceProjectionResolver.NO_Z ? null : new Target(x, y, z);
        }
        if (state.viewMode() == VisualizerViewMode.INTERIOR) {
            if (state.interior() == null
                    || x < state.interior().minX() || x > state.interior().maxX()
                    || y < state.interior().minY() || y > state.interior().maxY()) return null;
        }

        LandscapeSliceResolver.Cell slice = slices.resolve(x, y, state.selectedZ(), state.lowerDepth());
        return switch (slice.kind()) {
            case CURRENT_SURFACE -> new Target(x, y, state.selectedZ());
            case LOWER_SURFACE -> new Target(x, y, safeStandingZ(slice.terrainZ()));
            case SOLID_BODY, EMPTY -> null;
        };
    }

    private int selectionZAt(int x, int y) {
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            int standing = surfaces.standingZ(x, y);
            return standing == SurfaceProjectionResolver.NO_Z ? state.selectedZ() : standing;
        }
        return state.selectedZ();
    }

    private ViewPortal portalAt(int x, int y) {
        if (state.viewMode() == VisualizerViewMode.SURFACE) return portals.surfaceAt(x, y);
        if (state.viewMode() == VisualizerViewMode.INTERIOR && state.interior() != null) {
            return portals.interiorAt(state.interior().id(), x, y, state.selectedZ());
        }
        return null;
    }

    private void clearPreviewSearch() {
        if (previewSearch != null && previewSearch.status() == PathSearchStatus.RUNNING) {
            previewSearch.cancel();
        }
        previewSearch = null;
        previewKey = null;
    }

    private void closeMenu() {
        menu.close();
        menuObject = null;
        menuPortal = null;
    }

    private static int safeStandingZ(int terrainZ) {
        return terrainZ == Integer.MAX_VALUE ? Integer.MAX_VALUE : terrainZ + 1;
    }

    private record Target(int x, int y, int z) { }
    private record PreviewKey(
            ObjectId mover,
            int sourceX,
            int sourceY,
            int sourceZ,
            int targetX,
            int targetY,
            int targetZ) { }
}
