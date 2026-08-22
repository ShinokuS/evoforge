package io.github.evoforge.simulation.world.interaction;

import java.util.ArrayList;
import java.util.List;

/** Small reusable library of geometry-only reach profiles. */
public final class InteractionReachProfiles {
    private InteractionReachProfiles() { }

    /**
     * Reaches a cardinal target at the actor's standing level or one cell below it.
     *
     * <p>Lower targets additionally require the target column at actor level to be
     * physically open. This models reaching down over a free edge while preventing
     * interaction through a full cell occupying the space above the lower target.
     */
    public static InteractionReachProfile cardinalSameOrOneBelow() {
        List<InteractionReachPattern> patterns = new ArrayList<>(8);
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            int dx = direction[0];
            int dy = direction[1];
            patterns.add(InteractionReachPattern.direct(new CellOffset(dx, dy, 0)));
            patterns.add(new InteractionReachPattern(
                    new CellOffset(dx, dy, -1),
                    List.of(new CellOffset(dx, dy, 0))));
        }
        return new InteractionReachProfile(patterns);
    }
}
