package io.github.evoforge.visualizer;

import io.github.evoforge.simulation.world.object.ObjectId;

/**
 * Explicit bridge from presentation gestures to authoritative simulation commands.
 * The visualizer never mutates simulation state directly.
 */
public interface VisualizerCommandSink {

    VisualizerCommandSink NONE = new VisualizerCommandSink() {
        @Override public CommandFeedback moveTo(ObjectId objectId, int x, int y, int z) {
            return CommandFeedback.rejected("Move unavailable");
        }
        @Override public CommandFeedback cancelMove(ObjectId objectId) {
            return CommandFeedback.rejected("No cancellable move");
        }
    };

    CommandFeedback moveTo(ObjectId objectId, int x, int y, int z);

    CommandFeedback cancelMove(ObjectId objectId);

    record CommandFeedback(boolean accepted, String message) {
        public CommandFeedback {
            if (message == null) {
                throw new IllegalArgumentException("message must not be null");
            }
        }
        public static CommandFeedback accepted(String message) {
            return new CommandFeedback(true, message);
        }
        public static CommandFeedback rejected(String message) {
            return new CommandFeedback(false, message);
        }
    }
}
