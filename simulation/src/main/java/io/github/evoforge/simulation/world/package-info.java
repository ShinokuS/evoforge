/**
 * Objective world semantics for EvoForge.
 *
 * <p>Top-level packages are independent semantic concepts, not technical
 * lifecycle layers and not the first mechanics that consume them. Current
 * roots include Continuum infrastructure, authored material identity, terrain,
 * geometry, space (position/orientation/occupancy/placement/measurement),
 * navigation (including traversal/pathfinding), object identity/state, liquid
 * and water, soil, atmosphere, geology, sky exposure, and interaction access.
 * Cross-concept causal orchestration belongs under {@code simulation.mechanics},
 * while domain-neutral execution belongs under {@code simulation.kernel}.</p>
 *
 * <p>See ADR-026 and repository-root {@code AGENTS.md}. A new independent
 * capability must receive its own semantic home rather than being nested under
 * its first consumer.</p>
 */
package io.github.evoforge.simulation.world;
