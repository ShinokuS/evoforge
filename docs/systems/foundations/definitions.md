# Definitions

## In plain language

Definitions describe **what a type of thing is configured to be**. Runtime systems describe **what one particular thing is doing right now**.

For example, a Cow definition can say that Cows can move, see, become hungry and occupy a cell exclusively. It should not store where Cow #42 currently stands or how hungry Cow #42 currently is.

This separation lets many runtime instances share immutable content data while every mechanic keeps ownership of its own changing state.

## Current status

EvoForge uses stable textual content keys plus mechanic-specific immutable compiled definition stores. Content composition is aspect-based rather than one universal schema containing every field that any future mechanic might want.

Runtime numeric definition IDs are local optimized references; stable textual keys are the durable/content identity.

## Stable identity

Source definitions use namespaced textual keys:

```text
namespace:name
```

Examples:

```text
core:cow
core:topsoil
core:granite
```

Runtime systems may resolve those to typed numeric IDs such as `ObjectDefinitionId` or `MaterialDefinitionId`.

Important distinction:

```text
stable textual key     durable/content semantic identity
runtime numeric id     composition-local efficient reference
```

A future save/content-pack boundary must preserve stable identity and resolve the appropriate runtime ID during load. Persisting the accidental integer registration order would be incorrect.

## Aspect composition

A definition source contributes only the mechanics it actually supports.

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

Each mechanic owns the compiler/store for its own aspect.

```text
source definition
      ↓ generic stable-key loading
mechanic-specific aspect compiler
      ↓
immutable mechanic-owned definition lookup
      ↓
runtime mechanic
```

Generic loading does not become a giant switch over all possible mechanics, and runtime mechanics do not inspect arbitrary raw JSON.

Fundamental compiler/bootstrap registration is explicit rather than reflection/service-discovery magic so dependency/order stays visible and deterministic.

## Definition data versus runtime state

Use this test:

```text
Does the value describe every instance of this definition the same way?
    → definition data may be correct.

Can the value change independently for one runtime object/cell/process?
    → authoritative runtime state belongs to the owning mechanic.
```

Examples:

| Immutable definition/configuration | Mutable runtime state |
|---|---|
| Movement rate | current Movement action |
| exclusive Occupancy capability | current destination reservation |
| Water-wading profile | actual Water depth / actor position |
| Vision parameters | currently perceived cells/objects |
| Need configuration | current Need deficit |
| Stock capacity/config | current finite quantity |
| Growth configuration | current growth/replenishment state |
| landscape traversal cost | current Terrain presence/material at XYZ |
| Soil physical properties | retained constituent amounts |
| surface retention capacity | actual free-liquid volume |
| liquid viscosity | actual free-liquid identity/quantity at a cell |

Shape is also not a mutable field embedded inside landscape material definitions. It is Geometry state/override attached to Terrain anchor lifetime.

## Current object-definition aspects

Production composition currently has independent definition data for capabilities including:

- Movement rate;
- exclusive Occupancy;
- Water-wading depth/profile;
- Agent capabilities;
- Vision range/FOV;
- Need definitions/motivations;
- Need progression;
- Need-satisfaction/provider-use parameters;
- semantic Need-solution knowledge;
- finite Consumable Stock;
- Growth.

Absence has mechanic-specific meaning. For example:

```text
no Movement aspect  -> ordinary self-propelled Movement unavailable
no exclusive aspect -> object does not claim exclusive cell occupancy
no Water-wading data -> Water-neutral behavior under the current traversal contract
```

A definition may combine many aspects while the mechanics remain independent and never switch on `core:cow` merely because it happens to contain them.

## Current landscape-definition aspects

Landscape definitions currently compose independent data such as:

- `SurfaceTraversalCost` — intrinsic actor-independent cost contribution for a structurally valid edge;
- `SoilProperties` — pore capacity + material permeability for the reference-viscosity liquid;
- deterministic local Soil-capacity variation parameters;
- generic `SurfaceRetentionDefinitions` — microtopographic reserve of free liquid before same-Z horizontal runoff.

Soil and liquid properties are deliberately separated. Effective infiltration combines material permeability with the incoming liquid's viscosity rather than storing Water-specific infiltration tables inside Terrain definitions.

## Canonical landscape material baseline

The first shipped generated-world landscape baseline includes:

| Key | Traversal cost | Soil capacity | Soil permeability |
|---|---:|---:|---:|
| `core:granite` | 1000 | — | — |
| `core:topsoil` | 1050 | 550000 | 100000 |
| `core:soil` | 1100 | 450000 | 60000 |
| `core:sand` | 1300 | 350000 | 250000 |

These are **EvoForge model-v1 values**, not direct laboratory measurements.

Interpretation:

- traversal cost `1000` is the neutral relative cost baseline;
- Soil capacity uses normalized `CellVolume` units and represents available pore volume;
- permeability is the current material conductance calibrated for the reference-viscosity liquid;
- actual liquid-specific uptake combines this with `LiquidTransportProperties`.

The baseline intentionally expresses only relationships needed by existing mechanics:

- Granite has no Soil aspect and is non-porous under current Soil mechanics;
- Topsoil has the greatest pore capacity of the three porous baseline materials and moderate permeability;
- Soil has lower capacity and lower permeability than Topsoil;
- Sand has lower capacity but much higher permeability and a higher traversal cost.

The names do **not** automatically imply granular physics, erosion, compaction, wet-sand traversal or shoreline placement. Those require real mechanics/generated causes.

## Data-only extension rule

If a new material/object is fully expressible through existing aspects, adding it should be data-only.

For landscape material:

```text
add a new definition file with stable key + existing aspects
        ↓
normal deterministic directory loading
        ↓
existing generic aspect compilers
```

Ordinary content addition should not require:

- editing a central material enum/list;
- Java branching on the new key;
- modifying world-generation algorithms merely to recognize the name;
- changing unrelated mechanics.

A Java change is justified when the new content introduces a genuinely new mechanic that existing aspects cannot express.

## Liquid transport definitions

`LiquidTypeId` is an open identity. A liquid that participates in transport/infiltration must have registered immutable `LiquidTransportProperties`.

The current property is **kinematic viscosity**.

Missing required transport data is broken configuration, not an invitation to silently assume Water behavior.

Water is explicitly registered with the reference viscosity used by the accepted current transport calibration.

Do not create an omnibus liquid-property bag in anticipation of hypothetical chemistry. Add physical properties only when an actual mechanic consumes them.

## Immutability and runtime freeze

Mechanic definition stores are frozen when `SimulationAssembly.start()` begins runtime execution.

This includes the current production stores for Landscape/traversal/Soil/retention/liquid transport and Object/Movement/wading/Occupancy/Agent/Vision/Need/Stock/Growth definitions.

Runtime then observes immutable configuration while changing state remains in its authoritative system.

## Deterministic loading

Filesystem or unordered-map iteration order must not become hidden simulation semantics.

Stable keys, deterministic directory loading and explicit compiler registration keep startup reproducible.

If future content packs need override/priority rules, that priority becomes an explicit contract rather than relying on incidental file order.

## World-generation semantic authoring

The same philosophy applies to generation data. Human-authored generation definitions/intents express semantic character; domain calibrators derive exact physical/operational values.

Example:

```text
"ruggedness = 0.7"
```

means strong ruggedness on an authored normalized scale, not “maximum slope = exactly X subunits” unless the chosen revision calibrator defines that relationship.

This prevents content authors from having to duplicate implementation details of procedural algorithms.

See [World Generation](../world-generation/overview.md).

## Invariants

- Stable textual keys are content identity; runtime integers are local implementation IDs.
- Immutable definition meaning is separate from per-instance mutable state.
- Every aspect has one mechanic owner/compiler.
- Generic loading does not switch on every content/mechanic type.
- Missing required configuration fails explicitly rather than creating silent physics fallbacks.
- Existing mechanics + new ordinary content should normally mean data-only extension.
- Definition stores are immutable during started runtime.

## Current limitations

Not yet defined:

- save/persistence schema migration;
- mod/content-pack precedence/override policy;
- plugin packaging/service discovery;
- durable network content negotiation;
- general serialized definition-version compatibility.

The stable-key/runtime-ID distinction must survive those future decisions.

## Code and tests

Generic definition infrastructure lives under:

```text
simulation/.../definition/
```

Mechanic-specific compiled definition stores live with their owning domains. Canonical landscape definitions are under `assets/definitions/material` and are loaded through the normal bootstrap path in tests so the baseline cannot quietly become a parallel Java registry.

## Sources

**Internal EvoForge design.** Aspect ownership, stable-key/runtime-ID separation and semantic generation authoring are project architecture.

The current Soil hydraulic calibration that consumes semantic Soil descriptions has a direct scientific model source documented in [Soil Hydraulics](../environment/soil-hydraulics.md) and [References](../../references.md).

See [Objects](objects.md), [Runtime Composition](runtime.md), [Architecture](../../architecture.md), and [World Generation](../world-generation/overview.md).
