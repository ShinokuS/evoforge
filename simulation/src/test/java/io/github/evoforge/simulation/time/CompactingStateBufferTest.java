package io.github.evoforge.simulation.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CompactingStateBufferTest {

    @Test
    void millionChangesKeepOnlyBoundedRecentTail() {
        int tailLimit = 64;
        CompactingStateBuffer<Long, Long> buffer = new CompactingStateBuffer<>(0L, Long::sum, tailLimit);

        for (int i = 0; i < 1_000_000; i++) {
            buffer.append(1L);
        }

        assertEquals(1_000_000L, buffer.currentState());
        assertEquals(1_000_000L, buffer.appliedDeltas());
        assertTrue(buffer.tailSize() < tailLimit);
        assertEquals(1_000_000 / tailLimit, buffer.compactions());
    }

    @Test
    void forcedCompactionPreservesCurrentStateAndDropsTail() {
        CompactingStateBuffer<Integer, Integer> buffer = new CompactingStateBuffer<>(10, Integer::sum, 100);
        buffer.append(5);
        buffer.append(-2);

        buffer.compact();

        assertEquals(13, buffer.currentState());
        assertEquals(13, buffer.checkpointState());
        assertEquals(0, buffer.tailSize());
    }
}
