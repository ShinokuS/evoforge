# Definitions

## Purpose

Represent immutable content/configuration separately from mutable runtime state. Definitions describe what a content type is configured to be; authoritative systems own what individual runtime instances/cells/processes currently do or store.

## Stable identity

Source definitions use stable textual keys:

```text
namespace:name
```

Runtime systems use typed numeric ids such as `ObjectDefinitionId` and `LandscapeDefinitionId` for efficient references and type safety.

Runtime numeric ids are bootstrap-local and are **not durable persistence identity**. Future save/load or content-pack boundaries must preserve the stable textual key and resolve the current runtime id when loading.

## Composition-driven aspects

Definition source is composed from mechanic-specific aspects rather than one universal schema containing every future field.

Conceptually:

```json
{
  "key": "core:example",
  "aspects": {
    "movement": { "rate": 100 },
    "occupancy": { "exclusive": true }
  }
}
```

A mechanic explicitly registers/owns compilation of the aspect it understands. Generic definition loading handles stable identity and dispatch without becoming a switch over all world mechanics.

```text
source data
    ↓
stable definition catalog
    ↓
explicit mechanic compilation
    ↓
mechanic-owned immutable lookup/store
```

Fundamental bootstrap remains explicit rather than reflection/service-discovery driven so dependencies and order are visible and deterministic.

## Definition data versus runtime state

A useful ownership test is:

```text
Does this value describe every instance of the definition identically?
    -> definition data may be appropriate.

Can it change independently for one runtime object/cell/process?
    -> runtime state belongs to an authoritative system.
```

Examples:

```text
MovementRate / WaterWadingProfile       object definition data
exclusive-cell capability               object definition data
Agent/Vision/Need capability parameters object definition data
ConsumableStock/Growth configuration    object definition data
SurfaceTraversalCost                    landscape definition data
SoilHydrology / SurfaceWaterStorage      landscape definition data

MovementAction / MovementClaim           mutable Movement state
execution reservation                    mutable Occupancy state
Spatial XYZ                              mutable Spatial state
Need deficit / consumable quantity       mutable mechanic state
SoilMoisture / Water quantity             mutable environmental state
terrain presence/material per XYZ         mutable Terrain state
```

Definitions never own object existence, positions, movement progress, reservation lifetime, terrain-cell lifetime or finite resource quantity.

## Current object-definition aspects

Current production composition includes independent immutable stores for:

- `movement` / `MovementRate` — positive transition-cost units per simulation tick;
- `occupancy` — whether the definition requires an exclusive cell;
- Water wading profile — maximum terrestrial Water depth accepted by that mover;
- autonomous Agent capability declarations;
- Vision range/FOV;
- Needs and Need motivation thresholds;
- Need progression;
- Need satisfaction/provider use parameters;
- semantic Need-solution knowledge;
- finite Consumable Stock configuration;
- Growth configuration.

Absence has mechanic-specific meaning. For example, no Movement aspect means current ordinary self-propelled Movement is unavailable; no exclusive Occupancy aspect means the object is transparent to exclusive-cell claims; no Water-wading aspect means Water-neutral traversal under the current compatibility contract.

These aspects remain independent. A definition can combine them without forcing unrelated systems to know the content's concrete name/type.

## Current landscape-definition aspects

Landscape identity is also composed from independent mechanic data:

- `SurfaceTraversalCost` — actor-independent cost contribution for an already-valid structural edge;
- `SoilHydrology` — finite retained-moisture capacity and infiltration transfer limit;
- deterministic local Soil-capacity variation parameters;
- `SurfaceWaterStorage` — finite free-Water reserve before same-Z horizontal runoff becomes mobile.

Shape is Geometry state/override associated with a terrain anchor lifetime, not a mutable field inside the material definition.

A missing required traversal cost on terrain participating in an otherwise valid priced edge is broken content/bootstrap configuration rather than an invitation to silently substitute a fallback.

## Immutable compiled stores

Mechanic-owned definition stores are frozen when `SimulationAssembly.start()` begins runtime execution. Production composition currently freezes Landscape, traversal, Soil/Water-surface parameters, Object, Movement, Water-wading, Occupancy, Agent/Vision/Need/Stock/Growth definition stores before scheduled simulation work starts.

Runtime systems therefore observe immutable configuration while authoritative mutable state remains in its owner.

## Deterministic loading

Loading/compilation must not turn filesystem iteration or unordered-map order into accidental simulation semantics.

Stable identity resolution and explicit compiler/bootstrap registration keep startup deterministic. If future content packs need priority/override rules, that order must become an explicit contract rather than incidental directory behavior.

## Extension rule

When existing mechanics already express a new content type, adding that content should normally be data-only:

```text
existing mechanics + new definition source -> new content
```

Needing Java branches for every ordinary content definition is a warning that semantics are too centralized.

A genuinely new definition-backed mechanic normally adds:

```text
source aspect
mechanic-owned immutable compiled lookup
explicit bootstrap/compiler registration
runtime consumer
headless tests
system documentation
```

Do not reserve unused aspects for hypothetical mechanics.

One definition may contribute independently to many mechanics. Presentation bindings are separate again: visual family/name/art choices must not become hidden simulation definition semantics merely because they use the same stable content identity.

## Deferred

Persistence migrations, mod/content-pack override policy, plugin packaging and durable serialized schema-version policy remain future consumers. The stable-key-versus-runtime-id distinction must survive those decisions.
