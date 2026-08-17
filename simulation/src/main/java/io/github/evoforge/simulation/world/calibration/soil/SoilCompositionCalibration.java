package io.github.evoforge.simulation.world.calibration.soil;

/**
 * Model-owned calibration parameters for projecting normalized soil semantics into physical
 * composition. They are not authored Definition values and can be replaced as calibration evidence
 * improves.
 */
public record SoilCompositionCalibration(int maximumOrganicMatterPartsPerMillion) {
    public SoilCompositionCalibration {
        if (maximumOrganicMatterPartsPerMillion < 0
                || maximumOrganicMatterPartsPerMillion > SoilCompositionProfile.FRACTION_SCALE) {
            throw new IllegalArgumentException("maximum organic matter fraction must be physical");
        }
    }

    /** Current representative mineral-soil calibration; kept behind an explicit replaceable seam. */
    public static SoilCompositionCalibration representative() {
        return new SoilCompositionCalibration(50_000);
    }
}
