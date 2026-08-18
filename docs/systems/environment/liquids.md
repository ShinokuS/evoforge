# Liquids

## Purpose

Provide deterministic liquid mechanics that are not defined by Water-specific gameplay.

The current architecture separates two physical states:

```text
free liquid
    -> occupies world-cell free volume
    -> moves through Geometry

retained Soil liquid
    -> occupies pore capacity inside porous terrain
    -> does not participate in free hydraulic flow while retained
```

Water is the first production liquid identity. Other identities can reuse the same storage, transport and Soil-retention mechanics without copying Water code.

## Free-liquid ownership

`LiquidSystem` is the single authoritative owner of free-liquid quantity:

```text
XYZ -> dry
   or
XYZ -> one LiquidTypeId + finite volume
```

`LiquidLookup` is read-only. `LiquidStorage` is an implementation boundary; the current `SparseLiquidStorage` allocates state only for wet cells.

Liquid volume uses the normalized `CellVolume` scale:

```text
EMPTY = 0
FULL  = 1_000_000
```

The scale is a fraction of one discrete cell volume, not litres.

One runtime free-liquid world composes one `LiquidSystem` and one shared `LiquidFlowSystem`. Typed facades may filter this state, but must not create a second hydraulic authority over the same cells.

## Identity and transport definitions

`LiquidTypeId` is an open semantic identifier, not a central enum of known liquids. Concrete identities remain domain-owned; Water owns `WaterSystem.TYPE`.

A liquid that participates in transport must have explicit `LiquidTransportProperties`. The current physical property is kinematic viscosity, stored as square micrometres per second. `LiquidTransportDefinitions` is mutable only during composition and frozen for runtime use.

There is no silent transport default for arbitrary liquid identities. Missing transport data is a configuration error.

`LiquidTransportProperties.reference()` represents the reference viscosity of `1 mm²/s`. Water is registered with this reference value in `SimulationAssembly`, preserving the previously accepted Water cadence.

Transport uses deterministic fixed-point inverse-viscosity scaling:

```text
mobility(nominal, liquid)
    = nominal * referenceViscosity / liquidViscosity
```

with `CellVolume` bounds and integer arithmetic.

The factor is applied once to the planned hydraulic edge transfer. Aggregate source limiting remains a numerical conservation/stability bound and does not apply viscosity a second time.

The same mobility function converts Soil material permeability into a liquid-specific infiltration rate. This keeps material permeability and liquid viscosity independent rather than encoding identity-pair tables.

## Current free-liquid composition invariant

An occupied free-liquid cell currently contains one liquid type.

```text
wine + dry cell   -> allowed
wine + wine       -> allowed within capacity
wine + blood      -> no implicit transfer/mixing
```

`LiquidSystem.addAtMost(...)` never overwrites another resident type. The flow solver also rejects transfer across unlike occupied cells.

If several unlike sources simultaneously target the same dry destination, every contested inflow is suppressed for that step. Stable iteration order therefore cannot become an accidental mixing-priority rule.

This is an explicit temporary boundary, not a claim that real liquids cannot mix. Multi-component free-liquid composition remains a separate milestone.

## Generic hydraulic flow

`LiquidFlowSystem` owns deterministic redistribution of free-liquid quantity while `LiquidSystem` remains the state owner.

The solver uses:

- Shape-derived free capacity;
- shared face opening floors;
- hydraulic head from world Z and local liquid surface height;
- two-phase plan / limit / commit updates;
- exact finite-volume conservation;
- deterministic proportional limiting;
- fixed-point relaxation;
- explicit liquid transport properties;
- sparse active frontier and dormancy;
- aggregate coherent latest-step flow diagnostics.

Hydraulic Geometry does not switch on concrete identities such as Water, blood or wine.

`LiquidFlowProcess` owns scheduled cadence. It runs while the shared activity frontier contains work and stops at dormancy. A narrow `LiquidFlowPreparation` hook allows deterministic work immediately before transport; current hydrology uses it for Soil infiltration.

### Flow diagnostics and visible motion

The hydraulic solver may execute several simultaneous edge transfers through one cell in the same deterministic step. Diagnostics do not expose whichever individual edge happened to have the largest amount. They first sum the signed transfer vectors for that cell and publish the dominant axis of the resulting **coherent net transfer**.

This matters for a settling pool. Equal or nearly equal flows arriving from opposite sides are real hydraulic work, but they do not imply a strong surface current in either direction:

```text
400 east + 400 west -> no coherent net direction
500 east + 480 west -> small eastward net direction
```

`LiquidFlowSample` is therefore a read-side diagnostic projection, not a second velocity state and not part of conservation math.

Water presentation adds one further read-side distinction: a horizontal coherent flux is animated only when it is significant relative to the Water currently occupying that cell. The same small absolute flux can remain visible in a shallow puddle while being visually calm in a deep lake. Downward falling remains visible whenever an actual downward net transfer exists.

This visual significance rule never stops or modifies authoritative hydraulic work. The solver continues until its fixed-point transfer deadband produces no more work and the sparse activity frontier becomes dormant. Multi-cell regression coverage removes a localized volume from a 9×9 pool and proves exact mass accounting, local integer hydraulic equilibrium, dormancy and state stability after dormancy.

## Generic Soil retention

`SoilLiquidSystem` is the authoritative owner of retained liquid composition inside porous terrain.

Several constituents may occupy one Soil cell, but all compete for one material-owned pore capacity:

```text
Soil pore capacity = 100_000
retained Water     =  60_000
retained blood     =  25_000
retained wine      =   5_000
remaining capacity =  10_000
```

This retained composition is bookkeeping for porous occupancy. It does not implement molecular mixing, reactions, displacement, diffusion, phase separation or derived mixture properties.

`SparseSoilLiquidStorage` is the current sparse implementation. `SoilLiquidLookup` exposes constituent amounts and total retained volume. `SoilLiquidCellsLookup` exposes deterministic occupied-cell iteration.

## Soil physical properties

Porous behavior belongs to the terrain material through `SoilProperties`:

```text
capacity      -> total pore volume available to all retained liquids
permeability  -> nominal per-tick uptake conductance for the reference-viscosity liquid
```

A landscape definition without `SoilProperties` is non-absorbing.

`TerrainSoilPropertiesLookup` resolves material properties and optional deterministic coordinate-local capacity variation. Variation changes capacity only; it does not randomize retained contents or permeability and consumes no runtime RNG stream.

Infiltration for one contact step is bounded by:

```text
requested free volume
remaining shared pore capacity
viscosity-adjusted material permeability
```

Conceptually:

```text
effectiveRate
    = permeability * referenceViscosity / liquidViscosity

accepted
    = min(requested, remainingCapacity, effectiveRate)
```

All arithmetic is deterministic fixed-point integer math.

This replaces the old Water-only `SoilHydrology` / `SoilMoistureSystem` model and the speculative liquid×material interaction table. Different liquid uptake now emerges from independent physical inputs: material permeability and liquid viscosity.

## Free liquid -> Soil infiltration

`SoilLiquidInfiltrationSystem` handles every active free-liquid identity through the same mechanism before the hydraulic solve:

```text
active free liquid
       |
       v
supporting porous terrain
       |
       v
SoilLiquidSystem
       |
       +--> accepted constituent retained
       |
       `--> excess remains free
```

The amount successfully retained is exactly the amount removed from free-liquid authority. If pore capacity or the current permeability/viscosity rate is exhausted, the remainder stays free and can run off or form a puddle.

No Water-specific exchange adapter exists in the final composition. Runtime wiring uses `SoilLiquidInfiltrationSystem` directly.

## Surface retention before horizontal runoff

Supporting terrain may declare a generic `SurfaceRetentionDefinitions` capacity. `TerrainSurfaceRetentionLookup` exposes that material-owned microtopographic reserve through:

```java
capacityAt(x, y, z)
```

This is free-liquid volume retained on the supporting surface before same-Z horizontal runoff. It is distinct from Soil pore retention and remains part of authoritative free-liquid quantity.

The current capability is intentionally material-owned and liquid-neutral. If future wetting physics requires surface tension, contact angle or another liquid-dependent effect, add the physical capability that the mechanic actually needs rather than a concrete liquid/material pair switch.

Vertical falling is not reduced by this surface reserve.

The former Water-only `SurfaceWaterStorage*` definitions/lookups were removed; there is one generic surface-retention capability.

## Derived free-liquid surfaces

`LiquidSurfaceLookup` is a mutation-maintained sparse projection by XY column. It can resolve the highest free liquid overall or the highest cell of a requested `LiquidTypeId` without scanning world space.

Derived surfaces own no quantity. They exist for exposure, presentation and other read-side consumers.

## Water integration

Water remains a narrow semantic integration over the generic foundation:

```text
shared mechanics
  LiquidSystem
  LiquidFlowSystem
  SoilLiquidSystem
  SoilLiquidInfiltrationSystem
          |
          v
      WaterSystem
          |
          +-- precipitation source semantics
          +-- Water evaporation semantics
          +-- Water wading constraint
          `-- Water presentation/diagnostics
```

`WaterSystem` owns only the Water identity and typed read/mutation facade. `WaterFlowLookup.from(...)` filters generic coherent-flow diagnostics for Water; there is no second Water transport solver.

Current precipitation and evaporation remain Water-specific because atmosphere semantics are not automatically shared by every liquid. Reusing generic transport or Soil retention does not imply that another constituent satisfies Thirst, evaporates on Water's schedule, uses Water wading rules or shares Water presentation.

See [Water](water.md) and [Surface Hydrology](hydrology.md).

## Deliberately deferred

This foundation does not yet define:

- multi-component free-liquid cells;
- miscibility or phase separation;
- density layering and buoyancy;
- surface tension/contact-angle wetting physics;
- temperature, freezing or boiling;
- chemical reactions or biological decay;
- diffusion, displacement or leaching between retained Soil constituents;
- generic precipitation/evaporation for arbitrary liquids;
- generic traversal hazards or presentation for arbitrary liquids.

Kinematic viscosity is **not** deferred: it is already an explicit transport property used by free flow and porous infiltration.

## Engineering rules

1. Free-liquid quantity has one authoritative owner.
2. One free-liquid world has one shared hydraulic solver.
3. Liquid identities are open and domain-owned; generic code contains no concrete-liquid catalog or switches.
4. Every transported liquid has explicit transport properties; missing definitions fail rather than silently default.
5. Kinematic viscosity changes mobility once per physical transfer calculation.
6. Soil pore capacity and permeability belong to terrain material data.
7. All retained constituents share one Soil pore capacity.
8. Generic Soil infiltration accepts arbitrary liquid identities and combines material permeability with liquid viscosity.
9. Surface microtopographic retention is a separate free-liquid capability from Soil pore retention.
10. Unsupported free-liquid composition blocks explicitly instead of depending on collection ordering.
11. Retained multi-constituent state must not be mistaken for implemented chemistry.
12. Free-liquid flow and free-to-retained transfer remain deterministic and exactly volume-accounted.
13. Scheduling stops at hydraulic dormancy.
14. Flow diagnostics aggregate coherent net transfer and never become an alternative hydraulic state.
15. Presentation may suppress insignificant coherent motion but must never suppress authoritative flow work.
16. Presentation and typed facades are observers/adapters, never alternative liquid authorities.

See [Decision 007](../decisions/007-liquid-transport-and-composition-boundary.md) for the architectural boundary.
