package io.github.evoforge.visualizer.interaction;

import com.badlogic.gdx.Gdx;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.pathfinding.PathSearchStatus;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerCommandSink;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerViewMode;
import io.github.evoforge.visualizer.presentation.portal.ViewPortal;

/** Owns manual Move targeting, advisory path preview and Move command submission. */
final class VisualizerMoveTargetingController {
    private static final int PREVIEW_EXPANSIONS_PER_FRAME = 512;

    private final SimulationView view;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final VisualizerWorldInteractionResolver resolver;
    private VisualizerCommandSink commands = VisualizerCommandSink.NONE;
    private boolean enabled = true;
    private PathSearch previewSearch;
    private PreviewKey previewKey;

    VisualizerMoveTargetingController(
            SimulationView view,
            VisualizerState state,
            VisualizerCamera camera,
            VisualizerWorldInteractionResolver resolver) {
        if (view == null || state == null || camera == null || resolver == null) {
            throw new IllegalArgumentException("move targeting dependencies must not be null");
        }
        this.view = view;
        this.state = state;
        this.camera = camera;
        this.resolver = resolver;
    }

    void setCommands(VisualizerCommandSink commands) {
        if (commands == null) throw new IllegalArgumentException("commands must not be null");
        this.commands = commands;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) return;
        if (state.moveTargeting()) state.cancelMoveTargeting();
        clearPreviewSearch();
        state.clearMoveTargetPreview();
    }

    boolean enabled() {
        return enabled;
    }

    boolean begin(ObjectId objectId) {
        if (!enabled || objectId == null || !view.objects().isAlive(objectId)) return false;
        state.beginMoveTargeting(objectId);
        return true;
    }

    boolean cancelTargeting() {
        if (!state.moveTargeting()) return false;
        state.cancelMoveTargeting();
        clearPreviewSearch();
        return true;
    }

    /** Advances bounded mover-aware advisory planning only while Move targeting is active. */
    void update() {
        if (!enabled || !state.moveTargeting()) {
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
        ViewPortal portal = resolver.portalAt(hover.x(), hover.y());

        // A Surface portal can annotate a doorway physically covered by terrain above.
        // The whole cell is therefore an interaction cell, not a hidden Movement target.
        // Inside, the doorway remains an ordinary visible target.
        if (portal != null && state.viewMode() == VisualizerViewMode.SURFACE) {
            clearPreviewSearch();
            state.clearMoveTargetPreview();
            return;
        }

        WorldInteractionTarget target = resolver.visibleTargetAt(hover.x(), hover.y());
        if (target == null) {
            clearPreviewSearch();
            state.setMoveTargetPreview(
                    hover.x(),
                    hover.y(),
                    resolver.selectionZAt(hover.x(), hover.y()),
                    VisualizerState.MoveTargetStatus.BLOCKED);
            return;
        }

        int sourceX = view.transforms().x(mover);
        int sourceY = view.transforms().y(mover);
        int sourceZ = view.transforms().z(mover);
        PreviewKey key = new PreviewKey(
                mover,
                sourceX,
                sourceY,
                sourceZ,
                target.x(),
                target.y(),
                target.z());

        if (!key.equals(previewKey)) {
            clearPreviewSearch();
            previewKey = key;
            if (sourceX == target.x() && sourceY == target.y() && sourceZ == target.z()) {
                state.setMoveTargetPreview(
                        target.x(),
                        target.y(),
                        target.z(),
                        VisualizerState.MoveTargetStatus.REACHABLE);
                return;
            }
            previewSearch = view.moveTo().beginPreview(
                    mover,
                    target.x(),
                    target.y(),
                    target.z());
            if (previewSearch == null) {
                state.setMoveTargetPreview(
                        target.x(),
                        target.y(),
                        target.z(),
                        VisualizerState.MoveTargetStatus.BLOCKED);
                return;
            }
            state.setMoveTargetPreview(
                    target.x(),
                    target.y(),
                    target.z(),
                    VisualizerState.MoveTargetStatus.CHECKING);
        }

        advancePreview(target);
    }

    void submit(WorldInteractionTarget target) {
        if (!enabled || target == null) return;
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
                mover,
                target.x(),
                target.y(),
                target.z());
        if (feedback.accepted()) {
            state.selectCell(target.x(), target.y(), target.z(), null);
            state.finishMoveTargeting("");
            clearPreviewSearch();
        } else {
            state.setInteractionMessage(feedback.message());
        }
    }

    private void advancePreview(WorldInteractionTarget target) {
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

    private void clearPreviewSearch() {
        if (previewSearch != null && previewSearch.status() == PathSearchStatus.RUNNING) {
            previewSearch.cancel();
        }
        previewSearch = null;
        previewKey = null;
    }

    private record PreviewKey(
            ObjectId mover,
            int sourceX,
            int sourceY,
            int sourceZ,
            int targetX,
            int targetY,
            int targetZ) { }
}
