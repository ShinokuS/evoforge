package io.github.evoforge.visualizer.interaction;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerCommandSink;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerViewMode;
import io.github.evoforge.visualizer.presentation.portal.ViewPortal;
import io.github.evoforge.visualizer.presentation.portal.ViewPortalLookup;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;
import io.github.evoforge.visualizer.visual.SurfaceProjectionResolver;

/** Routes raw input into focused world-target, move-targeting and context-menu interaction owners. */
public final class VisualizerInteractionController extends InputAdapter {
    private final SimulationView view;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final VisualizerWorldInteractionResolver resolver;
    private final VisualizerMoveTargetingController moveTargeting;
    private final VisualizerContextMenuController menus;

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
        resolver = new VisualizerWorldInteractionResolver(view, state, surfaces, slices);
        moveTargeting = new VisualizerMoveTargetingController(view, state, camera, resolver);
        menus = new VisualizerContextMenuController(view, state, camera, resolver, moveTargeting);
    }

    public void configure(ViewPortalLookup portals, VisualizerCommandSink commands) {
        if (portals == null || commands == null) {
            throw new IllegalArgumentException("interaction bindings must not be null");
        }
        resolver.setPortals(portals);
        moveTargeting.setCommands(commands);
        menus.setCommands(commands);
        menus.close();
    }

    public void setManualMovementEnabled(boolean enabled) {
        menus.close();
        moveTargeting.setEnabled(enabled);
    }

    public VisualizerContextMenu menu() {
        return menus.menu();
    }

    public ViewPortalLookup portals() {
        return resolver.portals();
    }

    public void update() {
        moveTargeting.update();
    }

    public void resize(int width, int height) {
        menus.resize(width, height);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode != Input.Keys.ESCAPE) return false;
        return cancelOrBack();
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.RIGHT) {
            if (menus.visible()) {
                menus.close();
                return true;
            }
            return moveTargeting.cancelTargeting();
        }
        if (button != Input.Buttons.LEFT) return false;

        if (menus.visible()) {
            return menus.consumeClick(screenX, screenY);
        }

        VisualizerCamera.Cell cell = camera.cellAt(screenX, screenY);
        ViewPortal portal = resolver.portalAt(cell.x(), cell.y());
        ObjectId object = resolver.visibleObjectAt(cell.x(), cell.y());

        // LMB always inspects the world cell first. Objects and portals contribute
        // actions to that cell; their visual glyphs are not independent hit targets.
        inspectCell(cell, object);

        if (portal != null) {
            if (object != null && moveTargeting.enabled()) {
                menus.openCombined(object, portal, screenX, screenY);
            } else {
                menus.openPortal(portal, screenX, screenY);
            }
            return true;
        }

        if (moveTargeting.enabled() && state.moveTargeting()) {
            WorldInteractionTarget target = resolver.visibleTargetAt(cell.x(), cell.y());
            if (target == null) {
                state.setInteractionMessage("Destination is not walkable");
                return true;
            }
            moveTargeting.submit(target);
            return true;
        }

        if (object != null && moveTargeting.enabled()) {
            menus.openObject(object, screenX, screenY);
        }
        return true;
    }

    /** Esc: menu -> unfinished target -> interior -> slice view -> hosting screen. */
    public boolean cancelOrBack() {
        if (menus.visible()) {
            menus.close();
            return true;
        }
        if (moveTargeting.cancelTargeting()) {
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
        state.selectCell(
                cell.x(),
                cell.y(),
                resolver.selectionZAt(cell.x(), cell.y()),
                null);
    }
}
