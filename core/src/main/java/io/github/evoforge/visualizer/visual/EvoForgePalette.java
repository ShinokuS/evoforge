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

    public static final int WATER_BASE = 0x4394B6FF;
    public static final int WATER_DARK = 0x337C9EFF;
    public static final int WATER_LIGHT = 0x72C3D2FF;
    public static final int WATER_HIGHLIGHT = 0xA4DFE2FF;

    // Cutaway mass stays in the same restrained landscape family instead of
    // switching to a separate blue/black material. Depth is communicated by
    // renderer shading; these colours only provide subtle material contrast.
    public static final int CUT_BASE = 0x5A6A4EFF;
    public static final int CUT_LIGHT = 0x77866AFF;
    public static final int CUT_DARK = 0x414D3CFF;
    public static final int CUT_DEEP = 0x2A322AFF;
    public static final int CUT_EDGE = 0x929D82FF;

    public static final int OUTLINE = 0x29362DFF;

    private EvoForgePalette() {
    }
}
