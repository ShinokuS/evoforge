package io.github.evoforge.simulation.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WorldTest {

    @Test
    void ownsSingleObjectRepository() {
        World world = new World();

        assertNotNull(world.objects());
        assertSame(world.objects(), world.objects());
    }
}