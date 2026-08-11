# Definitions

EvoForge separates immutable content description from mutable runtime state. Definitions answer what a content type *is configured to be*; systems own what individual runtime instances *currently are doing or storing*.

## Stable source identity

Source definitions use string keys such as:

```text
namespace:name
```

This stable textual key is the persistence-facing identity. Runtime integer-backed typed ids are rebuilt during bootstrap and must not be treated as durable save identity.

## Generic definition infrastructure

The `simulation.definition` package currently contains:

```text
DefinitionId
DefinitionCatalog
DefinitionRegistry
DefinitionAspectCompiler
DefinitionCompilerRegistry
DefinitionFileReader
DefinitionDirectoryLoader
DefinitionLoader
```

The generic layer handles loading/order/registration mechanics without knowing object-specific or landscape-specific semantics.

## Composition-driven source data

Definitions are composed from aspects rather than one universal schema containing every mechanic field.

Conceptually:

```json
{
  "key": "core:example",
  "aspects": {
    "physical": {
      "mass": 1.0
    },
    "movement": {
      "rate": 100
    }
  }
}
```

A mechanic registers a compiler for its own aspect. The definition loader does not need a switch containing every future mechanic.

## Explicit compiler registration

`DefinitionCompilerRegistry` maps aspect names to `DefinitionAspectCompiler` implementations. Registration is explicit during bootstrap.

This deliberately avoids reflection or service discovery for fundamental simulation composition. Startup dependencies remain visible in code and testable deterministically.

## Two-pass identity/compilation idea

The architecture treats stable keys and compiled mechanic data as separate concerns. Definitions can first establish identity/catalog membership, then mechanic compilers resolve aspect data against known definitions.

This supports deterministic cross-definition references without making source order accidental semantics.

## Typed ids

Domain areas wrap generic/runtime ids in typed ids such as:

```text
ObjectDefinitionId
LandscapeDefinitionId
```

This prevents accidental mixing of definition domains in normal Java APIs.

A runtime typed id is optimized for fast system references. The stable string key remains the value that should survive save/load or content-pack boundaries.

## Object definitions

Object definition bootstrap lives under:

```text
world/object/definition/
```

`ObjectFactory` uses the compiled object definition catalog to create `WorldObject` instances with a valid `ObjectDefinitionId`.

Object definitions do not own mutable instance state such as position, health, inventory, or process progress. Runtime mechanics own that state separately.

### `movement` aspect

Ordinary self-propelled movement capability is definition-backed:

```json
{
  "key": "core:walker",
  "aspects": {
    "movement": {
      "rate": 100
    }
  }
}
```

Compilation path:

```text
movement aspect
    ↓
MovementDefinitionCompiler
    ↓
MovementDefinitions
    ↓
ObjectDefinitionId -> MovementRate
```

`movement.rate` must be a positive integer. It is expressed in traversal-cost units per simulation tick.

Absence of the aspect means the definition does not have the current ordinary Movement capability. This is intentional composition semantics, not an implicit default speed.

`MovementRate` is immutable definition data. Fractional timing carry and active `MovementAction` state are mutable per-object runtime state and therefore live in `MovementStateStore`, not in definitions.

## Landscape definitions

Landscape definition bootstrap is separate from object definitions:

```text
world/landscape/definition/
```

A terrain cell stores a `LandscapeDefinitionId`, not a `WorldObject` identity.

This is important because landscape content can use the same composition-driven definition infrastructure without inheriting object lifetime semantics.

### `traversal` aspect

Landscape materials can define their actor-independent base traversal price:

```json
{
  "key": "core:granite",
  "aspects": {
    "traversal": {
      "cost": 1000
    }
  }
}
```

Compilation path:

```text
traversal aspect
    ↓
LandscapeTraversalDefinitionCompiler
    ↓
LandscapeTraversalDefinitions
    ↓
LandscapeDefinitionId -> SurfaceTraversalCost
```

`traversal.cost` must be a positive integer. `1000` is the current neutral baseline used by the transition-cost model.

The value describes the intrinsic surface contribution of that landscape material. It does not encode actor-specific affinity and it does not decide whether a structural edge exists.

A valid Movement edge whose supporting terrain has no compiled traversal cost is considered broken configuration rather than an ordinary gameplay rejection. EvoForge intentionally does not silently substitute a fallback price because that would hide content/bootstrap errors and change deterministic timing.

## Current mechanic-specific compiled data

The project now has several examples of the intended pattern:

```text
physical aspect
    ↓
PhysicalDefinitionCompiler
    ↓
PhysicalDefinitions

movement aspect
    ↓
MovementDefinitionCompiler
    ↓
MovementDefinitions

traversal aspect
    ↓
LandscapeTraversalDefinitionCompiler
    ↓
LandscapeTraversalDefinitions
```

A new mechanic with per-definition configuration should generally add its own compiler and compiled definition store rather than adding fields to a central definition class.

The definition domain and mechanic domain do not have to be the same. `movement` is attached to `ObjectDefinitionId`; `traversal` is attached to `LandscapeDefinitionId` because the configured fact belongs to terrain material.

## Definition data versus runtime data

A useful boundary test is:

```text
Does the value describe every instance of this definition identically?
    -> definition data may be appropriate.

Can the value change independently for one runtime object/cell/process?
    -> it belongs to runtime state owned by a system.
```

For current Movement:

```text
MovementRate             -> object definition data
SurfaceTraversalCost     -> landscape definition data
MovementAction           -> runtime state
per-object timing carry  -> runtime state
Spatial XYZ              -> runtime state owned by Spatial
```

## Deterministic loading

Definition loading must not depend on filesystem iteration order or unordered-map traversal when order affects results. Explicit registration and stable identity resolution support deterministic startup.

If a future feature needs ordering between definition packs or overrides, that ordering must become an explicit contract rather than an accidental directory behavior.

Compilers that own mutable compiled stores finalize those stores in `finish()`. Current Movement and Landscape Traversal compilers freeze their definition stores so runtime consumers see immutable configuration after bootstrap.

## Adding content

When existing mechanics already express a new content type, adding content should require data only.

```text
existing mechanics + new definition JSON
    -> new content
```

For example, a new ordinary terrain material can choose another `traversal.cost` without modifying `MovementSystem` or `TransitionCostCalculator`.

Needing Java changes for every new ordinary content definition is a warning that mechanic/configuration boundaries are too centralized.

## Adding a definition mechanic

A new definition-backed mechanic normally needs:

```text
source aspect format
DefinitionAspectCompiler
mechanic-owned compiled definition store
explicit bootstrap registration
unit/loading tests
runtime consumer
```

Do not introduce an aspect without a consumer merely to reserve future configuration space.

The runtime consumer should depend on the compiled store or a narrow read boundary, not repeatedly parse source JSON during simulation.

## Persistence rule

Never serialize a runtime numeric definition id as the only durable identity. Save the stable key and rebuild/resolve the runtime id when loading under the current definition catalog.

This keeps save data resilient to load order and runtime id reassignment.

## Related documentation

- [Movement System](Movement-System.md) — how `movement.rate` and `traversal.cost` participate in timed movement.
- [Landscape and Terrain](Landscape-and-Terrain.md) — authoritative terrain state.
- [Object Model](Object-Model.md) — object identity and definition-backed creation.
