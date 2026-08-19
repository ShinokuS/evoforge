# ADR-001: Authoritative ownership and narrow capabilities

- Status: Accepted
- Scope: Global simulation architecture
- Decision: Every mutable authoritative fact has one owner; cross-system consumers receive only the narrow read or mutation capability they need.

## Context

As simulation domains accumulate, shared mutable world/cell/entity objects make it unclear which system is allowed to change a fact. They also let unrelated consumers reach through a broad object for whatever service happens to be available.

## Decision

For every mutable fact, EvoForge identifies one authoritative owner. Other systems depend on narrow semantic capabilities. Composition roots may hold owners in order to wire the application, but runtime observers and unrelated domains do not receive a universal mutable world/service object.

## Why

This keeps ownership questions answerable, allows storage to change behind contracts, makes headless fixtures small, and prevents presentation from becoming simulation authority accidentally.

## Consequences

- New mutation authority is explicit API surface.
- Derived caches/views may exist only if rebuildable from the owner.
- Cross-domain coordination happens through semantic capabilities or an explicit higher-level coordinator.
- A new mechanic should normally add its own state owner rather than expanding `WorldObject`, Terrain cells or a global World record.

## Alternatives considered

A universal mutable world/cell/entity object was rejected because it makes ownership implicit and encourages unrelated mechanics to accumulate in central structures.

## Current implementation

The rule is visible throughout current runtime ownership: Object Repository owns existence, Spatial owns object position, Landscape owns Terrain material/presence, Geometry owns Shape overrides, Liquid/Soil systems own their respective finite quantities, and Agent/Need/Movement systems own their own state. `SimulationView` exposes read capabilities rather than mutable owners.

## Related documentation

- [Architecture](../architecture.md)
- [Objects and Identity](../systems/foundations/objects.md)
- [Runtime Composition](../systems/foundations/runtime.md)
- [Landscape](../systems/environment/landscape.md)
