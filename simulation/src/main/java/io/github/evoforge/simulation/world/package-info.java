/**
 * Authoritative world-model domains and lifecycle boundaries.
 *
 * <p>Navigation rule for this package tree:</p>
 * <ul>
 *   <li>domain concepts live with the domain that owns them, not in generic technical buckets;</li>
 *   <li>derived terrain facts live under {@code terrain} (for example {@code terrain.surface});</li>
 *   <li>developer-only observation helpers live under {@code diagnostics};</li>
 *   <li>{@code genesis}, {@code atlas}, {@code preparation}, {@code materialization} and
 *       {@code bootstrap} remain explicit lifecycle boundaries because each has a distinct owner
 *       and handoff contract;</li>
 *   <li>{@code navigation} and {@code pathfinding} remain separate because navigation owns world
 *       traversal semantics while pathfinding owns route-search algorithms;</li>
 *   <li>new top-level packages require a durable independent world responsibility. A single helper
 *       or implementation detail must be nested under its owning domain instead.</li>
 * </ul>
 *
 * <p>Do not introduce root-level {@code util}, {@code common}, {@code misc}, {@code helpers} or
 * similarly ownership-free packages. Package structure is part of the architecture and should make
 * the owner of a concept discoverable from its path.</p>
 */
package io.github.evoforge.simulation.world;
