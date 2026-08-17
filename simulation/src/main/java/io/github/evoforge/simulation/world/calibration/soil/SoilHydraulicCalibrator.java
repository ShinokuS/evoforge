package io.github.evoforge.simulation.world.calibration.soil;

/** Replaceable preparation-time algorithm from physical soil composition to hydraulic facts. */
@FunctionalInterface
public interface SoilHydraulicCalibrator {
    SoilHydraulicProfile calibrate(SoilCompositionProfile composition);
}
