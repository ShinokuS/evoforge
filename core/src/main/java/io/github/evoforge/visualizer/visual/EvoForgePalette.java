package io.github.evoforge.visualizer.visual;

/**
 * Small canonical palette for the procedural debug world.
 *
 * <p>The landscape generator deliberately works from a restrained palette so
 * independently generated tiles keep one visual language. Values are RGBA8888
 * and are presentation-only.</p>
 */
public final class EvoForgePalette {

    public static final int GRASS_BASE = 0x6F8F50FF;
    public static final int GRASS_LIGHT = 0x8FAF66FF;
    public static final int GRASS_DARK = 0x4F713DFF;
    public static final int GRASS_DEEP = 0x385631FF;

    public static final int EARTH_BASE = 0x8A633FFF;
    public static final int EARTH_LIGHT = 0xAA7C4EFF;
    public static final int EARTH_DARK = 0x62462FFF;
    public static final int EARTH_SHADOW = 0x493628FF;

    // Horizontal cutaway material deliberately avoids the warm terrain hue.
    // It represents occluding mass/depth, not another walkable floor material.
    public static final int CUT_BASE = 0x343A3FFF;
    public static final int CUT_LIGHT = 0x66727AFF;
    public static final int CUT_DARK = 0x252B30FF;
    public static final int CUT_DEEP = 0x171C20FF;
    public static final int CUT_EDGE = 0x89969DFF;

    public static final int OUTLINE = 0x29362DFF;

    private EvoForgePalette() {
    }
}
