package io.github.evoforge.visualizer;

/** Presentation-only world viewing mode. */
public enum VisualizerViewMode {
    /** Default open-world view: one highest visible surface per XY column. */
    SURFACE,
    /** Context-local view entered through a structure/cave portal. */
    INTERIOR,
    /** Legacy whole-world Z slice retained only as a development tool. */
    DEBUG_SLICE
}
