package io.github.evoforge.simulation.world.atlas.hydrology;

/**
 * Minimum-barrier topological connection discovered between two standing-water components.
 *
 * <p>The meeting edge is diagnostic provenance for where two Priority-Flood influence regions met.
 * The actual maximum-height cell on the minimax path may lie earlier on either side of that edge.</p>
 */
public record StandingWaterSpillConnection(
        int firstBodyId,
        int secondBodyId,
        long barrierElevationSubunits,
        int meetingFirstX,
        int meetingFirstY,
        int meetingSecondX,
        int meetingSecondY) {

    public StandingWaterSpillConnection {
        if (firstBodyId < 0 || secondBodyId < 0 || firstBodyId >= secondBodyId) {
            throw new IllegalArgumentException("spill connection body ids must be ordered and distinct");
        }
        if (barrierElevationSubunits < 0L) {
            throw new IllegalArgumentException("distinct standing-water bodies require non-negative dry barrier");
        }
    }

    public boolean connects(int bodyId) {
        return firstBodyId == bodyId || secondBodyId == bodyId;
    }

    public int otherBodyId(int bodyId) {
        if (firstBodyId == bodyId) return secondBodyId;
        if (secondBodyId == bodyId) return firstBodyId;
        throw new IllegalArgumentException("body is not part of spill connection: " + bodyId);
    }
}
