package io.github.evoforge.visualizer.interaction;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerCommandSink;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerViewMode;
import io.github.evoforge.visualizer.presentation.portal.ViewPortal;

/** Owns context-menu state, action composition and execution for world interaction. */
final class VisualizerContextMenuController {
    private final SimulationView view;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final VisualizerWorldInteractionResolver resolver;
    private final VisualizerMoveTargetingController moveTargeting;
    private final VisualizerContextMenu menu = new VisualizerContextMenu();
    private VisualizerCommandSink commands = VisualizerCommandSink.NONE;
    private ObjectId menuObject;
    private ViewPortal menuPortal;
    private int viewportWidth = 1;
    private int viewportHeight = 1;

    VisualizerContextMenuController(
            SimulationView view,
            VisualizerState state,
            VisualizerCamera camera,
            VisualizerWorldInteractionResolver resolver,
            VisualizerMoveTargetingController moveTargeting) {
        if (view == null || state == null || camera == null || resolver == null || moveTargeting == null) {
            throw new IllegalArgumentException("context menu dependencies must not be null");
        }
        this.view = view;
        this.state = state;
        this.camera = camera;
        this.resolver = resolver;
        this.moveTargeting = moveTargeting;
    }

    VisualizerContextMenu menu() {
        return menu;
    }

    boolean visible() {
        return menu.visible();
    }

    void setCommands(VisualizerCommandSink commands) {
        if (commands == null) throw new IllegalArgumentException("commands must not be null");
        this.commands = commands;
    }

    void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewportWidth = width;
        viewportHeight = height;
        close();
    }

    boolean consumeClick(int screenX, int screenY) {
        if (!menu.visible()) return false;
        VisualizerContextMenu.Action action = menu.actionAt(screenX, screenY);
        if (action != null) {
            execute(action);
        } else {
            close();
        }
        return true;
    }

    void openObject(ObjectId objectId, int screenX, int screenY) {
        if (!moveTargeting.enabled()) return;
        menuObject = objectId;
        menuPortal = null;
        if (view.moveTo().isActive(objectId)) {
            menu.open(
                    screenX,
                    screenY,
                    viewportWidth,
                    viewportHeight,
                    "Object " + objectId,
                    VisualizerContextMenu.Action.MOVE,
                    VisualizerContextMenu.Action.CANCEL_MOVE);
        } else {
            menu.open(
                    screenX,
                    screenY,
                    viewportWidth,
                    viewportHeight,
                    "Object " + objectId,
                    VisualizerContextMenu.Action.MOVE);
        }
    }

    void openPortal(ViewPortal portal, int screenX, int screenY) {
        menuPortal = portal;
        menuObject = null;
        if (moveTargeting.enabled()
                && state.moveTargeting()
                && state.viewMode() == VisualizerViewMode.INTERIOR) {
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

    void openCombined(
            ObjectId object,
            ViewPortal portal,
            int screenX,
            int screenY) {
        if (!moveTargeting.enabled()) {
            openPortal(portal, screenX, screenY);
            return;
        }
        menuObject = object;
        menuPortal = portal;
        boolean active = view.moveTo().isActive(object);
        String title = "Object " + object + " + " + portal.label();

        if (state.viewMode() == VisualizerViewMode.INTERIOR) {
            if (state.moveTargeting()) {
                if (active) {
                    menu.open(
                            screenX,
                            screenY,
                            viewportWidth,
                            viewportHeight,
                            title,
                            VisualizerContextMenu.Action.MOVE,
                            VisualizerContextMenu.Action.CANCEL_MOVE,
                            VisualizerContextMenu.Action.MOVE_HERE,
                            VisualizerContextMenu.Action.RETURN_SURFACE);
                } else {
                    menu.open(
                            screenX,
                            screenY,
                            viewportWidth,
                            viewportHeight,
                            title,
                            VisualizerContextMenu.Action.MOVE,
                            VisualizerContextMenu.Action.MOVE_HERE,
                            VisualizerContextMenu.Action.RETURN_SURFACE);
                }
            } else if (active) {
                menu.open(
                        screenX,
                        screenY,
                        viewportWidth,
                        viewportHeight,
                        title,
                        VisualizerContextMenu.Action.MOVE,
                        VisualizerContextMenu.Action.CANCEL_MOVE,
                        VisualizerContextMenu.Action.RETURN_SURFACE);
            } else {
                menu.open(
                        screenX,
                        screenY,
                        viewportWidth,
                        viewportHeight,
                        title,
                        VisualizerContextMenu.Action.MOVE,
                        VisualizerContextMenu.Action.RETURN_SURFACE);
            }
            return;
        }

        if (active) {
            menu.open(
                    screenX,
                    screenY,
                    viewportWidth,
                    viewportHeight,
                    title,
                    VisualizerContextMenu.Action.MOVE,
                    VisualizerContextMenu.Action.CANCEL_MOVE,
                    VisualizerContextMenu.Action.ENTER);
        } else {
            menu.open(
                    screenX,
                    screenY,
                    viewportWidth,
                    viewportHeight,
                    title,
                    VisualizerContextMenu.Action.MOVE,
                    VisualizerContextMenu.Action.ENTER);
        }
    }

    void close() {
        menu.close();
        menuObject = null;
        menuPortal = null;
    }

    private void execute(VisualizerContextMenu.Action action) {
        switch (action) {
            case MOVE -> {
                ObjectId object = menuObject;
                close();
                moveTargeting.begin(object);
            }
            case MOVE_HERE -> {
                ViewPortal portal = menuPortal;
                close();
                if (moveTargeting.enabled()
                        && portal != null
                        && state.viewMode() == VisualizerViewMode.INTERIOR) {
                    WorldInteractionTarget target = resolver.visibleTargetAt(
                            portal.interiorX(),
                            portal.interiorY());
                    if (target == null) {
                        state.setInteractionMessage("Destination is not walkable");
                    } else {
                        moveTargeting.submit(target);
                    }
                }
            }
            case CANCEL_MOVE -> {
                ObjectId object = menuObject;
                close();
                if (moveTargeting.enabled() && object != null) {
                    VisualizerCommandSink.CommandFeedback feedback = commands.cancelMove(object);
                    state.setInteractionMessage(feedback.accepted() ? "" : feedback.message());
                }
            }
            case ENTER -> {
                ViewPortal portal = menuPortal;
                ObjectId selected = state.selectedObject();
                close();
                if (portal != null) {
                    state.enterInterior(portal.interior());
                    state.selectCell(
                            portal.interiorX(),
                            portal.interiorY(),
                            portal.interiorZ(),
                            selected);
                    camera.setView(
                            portal.interiorX() + 0.5f,
                            portal.interiorY() + 0.5f,
                            1f);
                    state.setInteractionMessage("");
                }
            }
            case RETURN_SURFACE -> {
                ViewPortal portal = menuPortal;
                ObjectId selected = state.selectedObject();
                close();
                state.leaveInterior();
                if (portal != null) {
                    state.selectCell(
                            portal.surfaceX(),
                            portal.surfaceY(),
                            portal.surfaceZ(),
                            selected);
                    camera.setView(
                            portal.surfaceX() + 0.5f,
                            portal.surfaceY() + 0.5f,
                            1f);
                }
                state.setInteractionMessage("");
            }
        }
    }
}
