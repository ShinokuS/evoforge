# Definitions

## Purpose

Represent immutable content/configuration separately from mutable runtime state. Definitions describe what a content type is configured to be; authoritative systems own what individual runtime instances, cells and processes currently store or do.

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

A mechanic explicitly registers and owns compilation of the aspect it understands. Generic definition loading handles stable identity and dispatch without becoming a switch over all world mechanics.

```text
source data
    ↓
stable definition catalog
    ↓
explicit mechanic compilation
    ↓
mechanic-owned immutable lookup/store
```

Fundamental bootstrap remains explicit rather than reflection/service-discovery driven so dependencies and order stay visible and deterministic.

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
MovementRate / WaterWadingProfile        object definition data
exclusive-cell capability                object definition data
Agent/Vision/Need capability parameters  object definition data
ConsumableStock/Growth configuration     object definition data
SurfaceTraversalCost                     landscape definition data
SoilProperties                           landscape definition data
SurfaceRetentionDefinitions              landscape material data
LiquidTransportProperties                liquid identity configuration

MovementAction / MovementClaim           mutable Movement state
execution reservation                    mutable Occupancy state
Spatial XYZ                              mutable Spatial state
Need deficit / consumable quantity       mutable mechanic state
Soil retained-liquid composition         mutable environmental state
free-liquid quantity/type                mutable environmental state
terrain presence/material per XYZ        mutable Terrain state
```

Definitions never own object existence, positions, movement progress, reservation lifetime, terrain-cell lifetime or finite resource/liquid quantity.

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

Absence has mechanic-specific meaning. For example, no Movement aspect means ordinary self-propelled Movement is unavailable; no exclusive Occupancy aspect means the object is transparent to exclusive-cell claims; no Water-wading aspect means Water-neutral traversal under the current contract.

These aspects remain independent. A definition can combine them without forcing unrelated systems to know the content's concrete name/type.

## Current landscape-definition aspects

Landscape identity is composed from independent mechanic data:

- `SurfaceTraversalCost` — actor-independent cost contribution for an already-valid structural edge;
- `SoilProperties` — shared pore `capacity` plus material `permeability` for the reference-viscosity liquid;
- deterministic local Soil-capacity variation parameters;
- generic `SurfaceRetentionDefinitions` — material microtopographic free-liquid reserve before same-Z horizontal runoff.

`SoilProperties` is intentionally physical/material-owned. Liquid-specific uptake is derived by combining material permeability with the incoming liquid's `LiquidTransportProperties`, not by storing Water-specific infiltration limits or liquid/material pair tables.

Shape is Geometry state/override associated with a terrain anchor lifetime, not a mutable field inside the material definition.

A missing required traversal cost on terrain participating in an otherwise valid priced edge is broken content/bootstrap configuration rather than an invitation to silently substitute a fallback.

## Canonical landscape material baseline

The first generated-world landscape baseline uses four stable content identities from `assets/definitions/landscape`:

| key | traversal cost | Soil capacity | Soil permeability |
| --- | ---: | ---: | ---: |
| `core:granite` | 1000 | — | — |
| `core:topsoil` | 1050 | 550000 | 100000 |
| `core:soil` | 1100 | 450000 | 60000 |
| `core:sand` | 1300 | 350000 | 250000 |

These numbers are canonical **model-v1 intrinsic values**, not a claim that one normalized cell or one simulation tick directly equals a particular real-world soil sample or laboratory measurement. `capacity` uses the normalized `CellVolume` scale and therefore represents available pore volume. `permeability` is the material conductance used for the reference-viscosity liquid; liquid viscosity is combined separately by the existing infiltration model. Traversal cost is relative to the neutral cost of `1000`.

The baseline encodes only first-order relationships required by existing mechanics:

- Granite is the neutral hard, non-porous baseline under the current Soil mechanic, so it intentionally has no `soil` aspect.
- Topsoil has the largest pore capacity of the three porous materials and moderate permeability.
- Soil has lower pore capacity and lower permeability than Topsoil.
- Sand has lower pore capacity but substantially higher permeability, and its loose surface is more costly to traverse.

This baseline does **not** add granular Sand simulation, erosion, compaction, saturation-dependent traversal, material-specific runoff rules, or a hidden content switch. Those require real future mechanics and must not be inferred from the material name.

### Ordinary material authoring contract

When a new material is fully expressible through existing aspects, authoring it is a data-only operation: add one definition file under the landscape definition directory containing its stable key and the aspects it actually supports. Directory loading discovers that file deterministically; existing aspect compilers consume it generically.

Ordinary material addition must not require editing a central material list, adding Java branches for the material key, changing world-generation algorithms, or modifying unrelated evaluators/calibration code. A Java change is justified only when the content introduces a genuinely new semantic mechanic that existing aspects cannot express.

The canonical-material test loads the shipped directory through the normal `LandscapeDefinitionBootstrap` with generic traversal and Soil compilers. This makes the authoring contract executable and prevents the four baseline materials from becoming a parallel hard-coded registry.

## Liquid transport definitions

`LiquidTypeId` is an open domain-owned identity. A liquid that participates in free transport or Soil infiltration must have `LiquidTransportProperties` registered during composition.

The current property is kinematic viscosity. `LiquidTransportDefinitions` is frozen at runtime like other definition stores, and missing transport data is treated as broken configuration rather than silently assuming Water behavior.

Water is explicitly registered with the reference viscosity used to calibrate the previously accepted Water transport rate.

Adding future physical properties must be driven by an actual mechanic. EvoForge does not maintain a speculative omnibus liquid property bag.

## Immutable compiled stores

Mechanic-owned definition stores are frozen when `SimulationAssembly.start()` begins runtime execution. Production composition freezes Landscape, traversal, Soil properties/variation, generic surface retention, liquid transport, Object, Movement, Water-wading, Occupancy, Agent/Vision/Need/Stock/Growth definition stores before scheduled simulation work starts.

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
