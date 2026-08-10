package io.github.evoforge.simulation.world.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;

final class NavigationCacheTest {

    @Test
    void storesZeroAndTransitionMasks() {
        NavigationCache cache =
                new NavigationCache();

        cache.put(
                1,
                2,
                3,
                TransitionMask.NONE);

        int east =
                TransitionMask.of(
                        1,
                        0,
                        0);

        cache.put(
                -4,
                5,
                -6,
                east);

        assertEquals(
                TransitionMask.NONE,
                cache.get(
                        1,
                        2,
                        3));

        assertEquals(
                east,
                cache.get(
                        -4,
                        5,
                        -6));

        assertEquals(
                NavigationCache.MISS,
                cache.get(
                        9,
                        9,
                        9));
    }

    @Test
    void overwritesExistingCoordinate() {
        NavigationCache cache =
                new NavigationCache();

        cache.put(
                1,
                2,
                3,
                TransitionMask.NONE);

        int north =
                TransitionMask.of(
                        0,
                        1,
                        0);

        cache.put(
                1,
                2,
                3,
                north);

        assertEquals(
                1,
                cache.size());

        assertEquals(
                north,
                cache.get(
                        1,
                        2,
                        3));
    }

    @Test
    void removesAndReusesEntries() {
        NavigationCache cache =
                new NavigationCache();

        for (int i = 0; i < 1000; i++) {
            cache.put(
                    i,
                    i * 17,
                    -i,
                    i & TransitionMask.ALL);
        }

        for (int i = 0; i < 1000; i += 2) {
            cache.remove(
                    i,
                    i * 17,
                    -i);
        }

        assertEquals(
                500,
                cache.size());

        for (int i = 0; i < 1000; i++) {
            int value = cache.get(
                    i,
                    i * 17,
                    -i);

            if ((i & 1) == 0) {
                assertEquals(
                        NavigationCache.MISS,
                        value);
            } else {
                assertEquals(
                        i & TransitionMask.ALL,
                        value);
            }
        }
    }

    @Test
    void survivesGrowthAndFullIntCoordinates() {
        NavigationCache cache =
                new NavigationCache();

        cache.put(
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                TransitionMask.NONE);

        cache.put(
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                TransitionMask.ALL);

        for (int i = 0; i < 10_000; i++) {
            cache.put(
                    i,
                    i * 31,
                    i * -17,
                    i & TransitionMask.ALL);
        }

        assertEquals(
                TransitionMask.NONE,
                cache.get(
                        Integer.MIN_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MIN_VALUE));

        assertEquals(
                TransitionMask.ALL,
                cache.get(
                        Integer.MAX_VALUE,
                        Integer.MIN_VALUE,
                        Integer.MAX_VALUE));

        for (int i = 0; i < 10_000; i++) {
            assertEquals(
                    i & TransitionMask.ALL,
                    cache.get(
                            i,
                            i * 31,
                            i * -17));
        }
    }

    @Test
    void clearDropsEveryEntry() {
        NavigationCache cache =
                new NavigationCache();

        cache.put(
                1,
                2,
                3,
                TransitionMask.ALL);

        cache.put(
                4,
                5,
                6,
                TransitionMask.NONE);

        cache.clear();

        assertEquals(
                0,
                cache.size());

        assertEquals(
                NavigationCache.MISS,
                cache.get(
                        1,
                        2,
                        3));

        assertEquals(
                NavigationCache.MISS,
                cache.get(
                        4,
                        5,
                        6));
    }
}
