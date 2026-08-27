package io.github.evoforge.simulation.world.continuum.field;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Authoritative bounded materialization seam for Continuum scalar pages.
 *
 * <p>Simple coordinate-addressable fields may use {@link ContinuumMaterializer}. Algorithms that
 * need a bounded halo or other local preparation may implement this contract directly. In either
 * case only the requested page is returned and cache/page boundaries remain representation details,
 * never natural feature boundaries.</p>
 */
public interface ContinuumScalarPageSource {
    ContinuumWorldDomain domain();

    ContinuumScalarPage materialize(ContinuumSampleWindow window);
}
