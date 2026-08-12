# Definitions

## Purpose

Represent immutable content/configuration separately from mutable runtime state. Definitions describe what a content type is configured to be; authoritative systems own what individual runtime instances currently are doing or storing.

## Stable identity

Source definitions use stable textual keys:

```text
namespace:name
```

Runtime systems use typed numeric ids such as `ObjectDefinitionId` and `LandscapeDefinitionId` for efficient references and type safety.

Runtime numeric ids are rebuilt during bootstrap and are **not durable persistence identity**. Save/load or content-pack boundaries must preserve the stable textual key and resolve the current runtime id when loading.

## Composition-driven aspects

Definition source is composed from mechanic-specific aspects rather than one universal schema containing every future field.

Conceptually:

```json
{
  "key": "core:example",
  "aspects": {
    "movement": { "rate": 100 }
  }
}
```

A mechanic explicitly registers a compiler for the aspect it owns. The generic loader handles definition identity/loading order and compiler dispatch without a central switch over all world mechanics.

```text
source JSON
    ↓
stable definition identity/catalog
    ↓
explicit aspect compiler registration
    ↓
mechanic-owned immutable compiled store
```

Fundamental bootstrap uses explicit registration rather than reflection/service discovery so startup dependencies remain visible and deterministic.

## Definition data versus runtime state

A useful ownership question is:

```text
Does this value describe every instance of the definition identically?
    → definition data may be appropriate.

Can it change independently for one runtime object/cell/process?
    → runtime state belongs to an authoritative system.
```

Current examples:

```text
MovementRate             object definition data
SurfaceTraversalCost     landscape definition data
MovementAction           mutable Movement runtime state
per-object timing carry  mutable Movement runtime state
Spatial XYZ              mutable Spatial runtime state
terrain presence          mutable Terrain runtime state
```

Definitions never own object existence, positions, movement progress or terrain-cell lifetime.

## Current compiled aspects

### Object movement

Object definition `movement.rate` compiles to:

```text
ObjectDefinitionId → MovementRate
```

The value is a positive number of transition-cost units per simulation tick. Absence of the aspect means the current ordinary self-propelled Movement capability is unavailable; there is no implicit default speed.

### Landscape traversal

Landscape definition `traversal.cost` compiles to:

```text
LandscapeDefinitionId → SurfaceTraversalCost
```

The value is positive and the current neutral baseline is `1000`. It describes actor-independent surface contribution, not structural connectivity and not actor-specific affinity.

If terrain participates in an otherwise valid movement edge but required traversal data is missing, that is broken definition/bootstrap configuration. EvoForge does not silently substitute a fallback price that would hide content errors and alter deterministic timing.

## Deterministic loading

Loading/compilation must not make filesystem iteration or unordered-map order into accidental simulation semantics.

Stable identity resolution and explicit compiler registration support deterministic startup. If future packs need override/priority ordering, that order must become an explicit contract rather than incidental directory behavior.

Mechanic-owned compiled stores are finalized/frozen after bootstrap so runtime consumers observe immutable configuration.

## Extension rule

When existing mechanics already express a new content type, adding that content should normally be data-only:

```text
existing mechanics + new definition source → new content
```

Needing Java branches for every ordinary content definition is a warning that content semantics are too centralized.

A genuinely new definition-backed mechanic normally adds:

```text
source aspect
mechanic-specific compiler
mechanic-owned compiled lookup
explicit bootstrap registration
tests
real runtime consumer
```

Do not reserve unused aspects for hypothetical mechanics. Runtime systems consume compiled data/read contracts rather than repeatedly parsing source JSON.

## Deferred

Persistence migrations, content-pack/mod override policy and plugin packaging remain future consumers. The stable-key-versus-runtime-id distinction must survive those decisions.
