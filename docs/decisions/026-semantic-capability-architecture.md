# ADR-026: Semantic capability architecture

- Status: Accepted
- Scope: Repository-wide simulation decomposition, reuse boundaries and dependency direction
- Supersedes: ADR-025 block taxonomy and any rule that places a reusable concept under its first consumer
- Decision: EvoForge is decomposed into independent **semantic modules**. A semantic module represents one domain concept that can evolve independently and exposes consumer-neutral capabilities. Architectural roles such as authority, capability, algorithm, projection and process are orthogonal roles inside/around semantic modules rather than mutually exclusive package categories. Mechanics are consumers/orchestrators of semantic capabilities and never own a capability merely because they use it first.

## Problem discovered during the architecture reset

The initial owner-first reset fixed one serious problem: it stopped scattering one concept across global technical layers such as `generation`, `physics` and `storage`.

However, ADR-025 then introduced another over-constraint by requiring every production block to be exactly one of `OWNER`, `MECHANIC`, `KERNEL`, `PROJECTION` or `COMPOSITION`.

That classification is too rigid for a simulation built from reusable world concepts.

Example: occupancy was initially used by Movement. If Occupancy were therefore placed inside `mechanics/movement`, a later Drop/Place/Spawn/Build mechanic would either:

- depend on Movement internals;
- duplicate occupancy rules/state;
- or force a repository-wide extraction/refactor.

All three outcomes violate the goal of modularity.

The same problem applies to Navigation, Pathfinding, Visibility, Geometry, Interaction Access, Spatial placement, containment, ownership, attachment, collision/admission and many future concepts. Their first consumer does not define their semantic owner.

## Core decision

### 1. The primary architectural unit is the semantic module

A semantic module answers:

> What independent concept does this code represent, and can another part of the simulation use that concept without knowing the feature that first needed it?

Examples of plausible independent semantic modules:

```text
world/object
world/space
world/space/occupancy
world/geometry
world/navigation
world/visibility
world/terrain
world/liquid
world/soil
world/atmosphere
agents
```

The exact set grows only when real concepts exist. The important rule is that packages are named by the concept, not by the first use case.

### 2. Reusable capability must not live under a consumer

Let `C` be a capability and `U` a consumer/use case.

If the meaning of `C` can be defined without mentioning `U`, then:

```text
C ∉ internal(U)
```

and the dependency direction is:

```text
U -> C
```

not:

```text
C -> U
```

and not:

```text
U/C mixed package
```

Examples:

- Occupancy can be defined without Movement -> Occupancy is independent of Movement.
- Navigation can be defined without MoveTo -> Navigation is independent of MoveTo.
- Pathfinding can be defined without Agent decision making -> Pathfinding is an independent navigation capability/algorithm.
- Geometry can be defined without traversal policy -> objective Geometry is independent of Movement.
- A generic object placement/admission rule can be used by Spawn, Drop, Build and Movement -> it belongs to a space/placement capability, not any one mechanic.

### 3. Architectural roles are orthogonal, not exclusive block types

A semantic module may contain several roles when they all express the same concept and change together:

- **AUTHORITY** — authoritative mutable state owned by the concept;
- **CAPABILITY** — stable operation/query offered to arbitrary consumers;
- **POLICY / ALGORITHM** — replaceable rule or solver behind that capability;
- **PROJECTION** — rebuildable derived index/cache/view;
- **PROCESS** — lifecycle behavior intrinsic to that concept;
- **GENESIS** — initialization intrinsic to that concept.

Repository-wide technical roles remain:

- **KERNEL** — domain-neutral execution machinery;
- **COMPOSITION** — explicit wiring/lifecycle selection;
- **WORKFLOW / MECHANIC** — a higher-level process that coordinates independent semantic modules through their public capabilities.

A semantic module is not split merely because it contains more than one role.

### 4. One authority per fact still holds

For every mutable fact `F`:

```text
|authority(F)| = 1
```

This rule is unchanged.

A reusable capability may read several authorities and may own its own genuinely independent state (for example an occupancy reservation), but it may not create a second authoritative copy of another concept's fact.

### 5. Capability reuse is a first-class design requirement

Before placing code inside a mechanic/feature, ask:

1. Does this concept make sense without that mechanic?
2. Could another plausible current or future mechanic need the same semantics?
3. Would a second consumer otherwise have to import the first consumer or duplicate rules?

If yes, the concept is an independent semantic module/capability now, even if there is currently only one consumer.

This is not speculative abstraction. The abstraction is justified by the concept's independent semantics, not by an imagined implementation hierarchy.

### 6. Mechanics compose capabilities; they do not become capability warehouses

A mechanic represents a causal workflow or law.

Example:

```text
Movement
  -> Navigation
  -> Traversal
  -> Occupancy
  -> Object position mutation
  -> Time/Scheduling
```

Movement owns only movement-specific process state and policy.

A later DropItem mechanic may use:

```text
DropItem
  -> Occupancy
  -> Object placement/position mutation
```

without depending on Movement.

A Build mechanic may use the same Occupancy capability. No shared rule moves when the new mechanic is introduced.

### 7. Dependencies follow semantic stability, not feature chronology

More fundamental/reusable concepts must not depend on higher-level workflows that consume them.

Typical direction:

```text
kernel / neutral values
        ↑
fundamental semantic concepts
        ↑
reusable semantic capabilities
        ↑
mechanics / workflows
        ↑
agents / scenario orchestration / presentation adapters
```

This is a dependency principle, not a mandatory folder-layer tree. Packages remain organized by semantic concept.

A cycle indicates either:

- a missing smaller capability/contract;
- two concepts that are actually one semantic module;
- or a workflow incorrectly embedded in a lower-level module.

### 8. Public APIs describe meaning, not consumers

A reusable public contract must not be named after a consumer unless the contract is truly consumer-specific.

Prefer:

```text
OccupancyView
CellAdmission
CellReservation
PositionView
ObjectPlacement
NavigationGraph
PathQuery
VisibilityQuery
```

Avoid reusable contracts whose names bake in one caller:

```text
MovementOccupancy
DropPlacementHelper
AgentNavigationService
```

unless the semantics genuinely are specific to that consumer.

### 9. Consumer-neutral state belongs with the semantic capability

Occupancy is the reference case.

Actual object position remains owned by the position/spatial authority. Occupancy may derive present occupancy from positions and definitions, while independently owning reservation claims if reservations are a real world/execution fact.

Thus Occupancy can expose admission/reservation capabilities to any consumer without duplicating object positions.

The exact package boundary may be `world/space/occupancy` or another equally precise semantic location after dependency audit; it must not be `mechanics/movement/occupancy`.

### 10. Algorithms are replaceable behind the semantic capability

Pathfinding is not owned by Movement merely because Movement requests paths.

A navigation/path capability can hide interchangeable algorithms:

```text
PathQuery
  <- ExactAStarPathfinder
  <- future hierarchical/other solver
```

Movement, Agents or other consumers depend on the path capability, not on each other.

Likewise, spatial indexes, occupancy indexes and geometry acceleration structures remain implementation/projection details behind their semantic module.

### 11. No global `capabilities/` dumping ground

Capability orientation does **not** introduce a technical root package named `capabilities`, `services` or `shared`.

Capabilities live with the semantic concept they express:

```text
world/space/occupancy
world/navigation
world/visibility
world/liquid
```

The repository must still scream domain meaning.

### 12. Promotion/extraction must be unnecessary for foreseeable reuse

A new consumer should normally be implementable by importing an existing public semantic capability, not by moving that capability out of another feature first.

This becomes an architecture acceptance test:

> If a new plausible consumer of concept C would force C to move packages, C is currently placed too low under a consumer.

### 13. Internal cohesion still matters

Do not extract every helper just because two call sites might someday exist.

A separate semantic module/capability is justified when its contract has independent domain meaning and can be explained/tested without its current consumer.

Implementation-only helpers stay internal.

The distinction is:

```text
independent domain meaning -> semantic capability
implementation convenience -> private/internal helper
```

### 14. Genesis, physics, storage and performance remain secondary axes

ADR-025 was correct that global `generation/<domain>`, `physics/<domain>` and `storage/<domain>` trees scatter concepts.

That rule remains.

For example Liquid may contain authority, flow solver, genesis and storage details because all are Liquid-specific. If Liquid exposes a consumer-neutral capability, multiple mechanics use it directly.

### 15. Architecture fitness tests must enforce reuse boundaries

CI should progressively verify:

- mechanics/workflows do not expose general-purpose capabilities through their internals;
- lower-level semantic modules do not depend on their consumers;
- no foreign `internal` imports;
- no semantic dependency cycles;
- no duplicate authorities;
- public capability packages/types do not import consumer-specific mechanics where avoidable;
- generic dumping-ground packages do not appear;
- simulation remains independent of presentation;
- Continuum remains independent of concrete natural domains.

## Concrete consequence for the current refactor

The previous planned move:

```text
navigation + pathfinding + occupancy -> mechanics/movement
```

is rejected.

Instead, these concepts must be audited independently:

- **Occupancy** — reusable spatial/world admission and reservation concept;
- **Navigation** — reusable traversability/connectivity capability;
- **Pathfinding** — algorithm/capability within or adjacent to Navigation, consumer-neutral;
- **Geometry** — objective world geometry, independent of movement policy;
- **Spatial/Position** — authoritative object location plus rebuildable indexes;
- **Movement** — a workflow/mechanic consuming those capabilities.

Future mechanics such as Drop, Place, Spawn, Build, Push, Teleport or interaction access should reuse the same semantic capabilities without moving or duplicating them.

## Why this is more stable

Parnas's information-hiding criterion says modules should hide decisions likely to change independently rather than mirror processing steps. A reusable concept such as Occupancy changes for occupancy reasons, not because Movement changes. It therefore deserves an independent module boundary. See: https://doi.org/10.1145/361598.361623

Component-based design likewise treats reusable components as independently meaningful units with explicit interfaces rather than feature-owned implementation fragments. The useful lesson for EvoForge is component independence and consumer-neutral interfaces, not adopting a component framework.

DDD's domain-service idea provides a related distinction: important domain behavior can be meaningful without naturally belonging to one entity. EvoForge uses the broader term semantic capability because capabilities may be stateful (for example reservations) and are not restricted to stateless services.

## Relationship to ADR-025

ADR-025 remains correct on these points:

- one `:simulation` module;
- semantic rather than technical package names;
- one authority per mutable fact;
- owner-local Genesis/physics/storage;
- narrow public surfaces and hidden internals;
- explicit acyclic dependencies;
- neutral kernel;
- no universal contexts/service locators/global event bus;
- performance representations remain internal.

ADR-026 supersedes these ADR-025 ideas:

- the requirement that every block have exactly one of five mutually exclusive types;
- the implication that all cross-owner reusable behavior must be a mechanic;
- any plan to place reusable Navigation/Pathfinding/Occupancy capabilities under Movement or another first consumer.

## Current implementation status

PR #132 remains Draft. Further semantic package migration is blocked until current packages are reclassified using this ADR. Mechanical module collapse already performed in the PR remains valid because it does not depend on the rejected capability placement.

## Related documentation

- `AGENTS.md` — operational repository laws
- `docs/architecture.md` — canonical architecture overview
- ADR-025 — earlier owner-first reset; partially superseded by this ADR
- ADR-023 — historical Gradle-module architecture, superseded
