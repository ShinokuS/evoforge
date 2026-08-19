# Liquids

## In plain language

EvoForge models liquid as **finite volume that must actually fit somewhere**. Water is the first real liquid, but the storage/flow/Soil-retention foundation is generic: the hydraulic solver does not contain a list saying “if Water do this, if blood do that”.

The model distinguishes two physical states:

```text
free liquid
  occupies open cell volume and can flow through Geometry

retained Soil liquid
  occupies pore capacity inside porous Terrain and does not participate in free flow while retained
```

A puddle, lake or falling column is therefore free liquid. Water held inside Soil is retained constituent state owned by a different system.

## Current status

Implemented now:

- one open `LiquidTypeId` identity per occupied free-liquid cell;
- finite normalized free-liquid volume;
- deterministic Geometry-aware hydraulic redistribution;
- explicit kinematic-viscosity transport property;
- generic porous-Soil infiltration for any configured liquid;
- several retained constituents sharing one Soil pore capacity;
- generic material-owned surface retention before same-level runoff;
- sparse mutation-maintained surface/active-frontier projections;
- deterministic dormancy when no meaningful hydraulic transfer remains;
- source-neutral flow diagnostics that can be filtered by liquid identity.

Free-liquid mixtures, density/buoyancy, pressure/inertia, surface tension and chemistry are deliberately not implemented.

## Free-liquid ownership

`LiquidSystem` is the single authoritative owner of free liquid:

```text
XYZ -> dry
or
XYZ -> LiquidTypeId + finite volume
```

`LiquidLookup` is read-only. Current `SparseLiquidStorage` is an implementation detail that allocates state only for wet cells.

One runtime liquid world has one shared `LiquidSystem` and one shared `LiquidFlowSystem`. Typed facades such as `WaterSystem` filter/adapt that truth; they do not create another hydraulic authority.

## Volume units

Free-liquid quantity uses normalized `CellVolume`:

```text
EMPTY = 0
FULL  = 1_000_000
```

This is a fraction of one simulation cell's free volume, **not litres**.

When a mechanic needs real physical volume (for example Water drinking), `PhysicalSpaceScale` provides the cell's real size and the mechanic performs the conversion explicitly.

## Liquid identity and transport properties

`LiquidTypeId` is an open semantic identifier. Water owns its own `WaterSystem.TYPE`; generic liquid code does not maintain a central enum of all possible liquids.

A transported liquid must have explicit `LiquidTransportProperties`. The current property is **kinematic viscosity**, stored in square micrometres per second.

Reference viscosity:

```text
1 mm²/s
```

is represented by `LiquidTransportProperties.reference()`. Production Water uses that reference value, preserving the accepted baseline cadence.

Missing transport data is invalid configuration; there is no silent “assume Water” fallback.

## Viscosity and mobility

The current deterministic inverse-viscosity scaling is conceptually:

```text
mobility(nominal, liquid)
    = nominal * referenceViscosity / liquidViscosity
```

where:

- `nominal` is the amount/rate the reference-viscosity model would allow;
- `referenceViscosity` is the model reference;
- `liquidViscosity` is the configured constituent property.

A more viscous liquid therefore transfers/infiltrates more slowly under otherwise identical conditions.

The viscosity factor is applied once to the physical transfer rate. Aggregate source limiting later enforces conservation/stability and must not apply viscosity a second time.

## Current free-liquid composition rule

One occupied free-liquid cell currently contains **one** liquid type.

```text
wine + dry cell   -> allowed
wine + wine       -> allowed up to capacity
wine + blood      -> no implicit mixing/overwrite
```

`LiquidSystem.addAtMost(...)` never replaces another resident type.

If unlike occupied cells meet, transfer across that interface is blocked. If several unlike sources target the same dry destination in one solve, contested inflows are suppressed rather than letting iteration order decide which identity “wins”.

This is a deterministic temporary boundary, not a physical claim that real liquids cannot mix.

## Geometry-aware hydraulic model

`LiquidFlowSystem` redistributes free-liquid volume while `LiquidSystem` remains the quantity owner.

The solver uses neutral Geometry facts:

- cell free capacity;
- `freeVolumeBelow(height)` profiles;
- physical face opening floors;
- world-space Z;
- current local liquid surface height.

It never branches on concrete Shape classes.

### Hydraulic head

The useful scalar for current local transfer is conceptually:

```text
hydraulicHead = worldCellBaseHeight + localLiquidSurfaceHeight
```

Two adjacent compatible free regions can exchange liquid when their heads differ enough and the shared face is physically open at the relevant level.

Local liquid surface height is derived by inverting the Shape's free-volume profile through `CellSpace.surfaceHeight(...)`; equal volume can therefore produce different height on different geometry.

### Vertical falling

A valid downward physical opening allows falling into lower free capacity. Surface-retention reserve does not reduce valid vertical falling.

### Two-phase plan / limit / commit

A hydraulic step is structured so iteration order cannot spend volume twice:

```text
1. inspect one stable pre-step state
2. plan candidate directed transfers
3. apply per-source aggregate limits proportionally
4. resolve unsupported unlike-liquid contention
5. commit all accepted transfers
6. publish diagnostics / next active frontier
```

No destination is incrementally filled while later neighbors are still planning against a different state.

### Conservation

For pure transport, total free-liquid volume is exactly conserved:

```text
Σ volume_before = Σ volume_after
```

Sources/sinks such as precipitation, evaporation, drinking or Soil retention report their own exact transferred amount separately.

### Fixed-point relaxation and dormancy

The solver uses deterministic integer/fixed-point transfer rules and a small transfer deadband/relaxation policy to converge to a stable local state rather than oscillating forever around sub-unit differences.

Only cells in the sparse active frontier are reconsidered. When no candidate work remains:

```text
active frontier empty
→ LiquidFlowProcess becomes dormant
→ no periodic hydraulic task remains
```

A later successful free-liquid mutation activates the affected region again.

This is simulation scheduling behavior, not renderer LOD.

## Generic Soil retention

`SoilLiquidSystem` owns retained constituent amounts inside porous Terrain.

Multiple constituents can coexist because retained state is a composition map sharing one material-owned capacity:

```text
pore capacity        = 100_000
retained Water       =  60_000
retained constituent B = 25_000
retained constituent C =  5_000
remaining capacity  =  10_000
```

This represents finite pore occupancy only. It does not simulate chemical mixing, diffusion, reactions, phase separation or displacement.

## Soil infiltration

Porous Terrain supplies current `SoilProperties`:

```text
capacity      total retained-liquid pore volume
permeability  nominal reference-viscosity uptake per tick
```

A material without Soil properties is non-absorbing.

For an incoming liquid:

```text
effectiveRate
  = permeability * referenceViscosity / liquidViscosity

accepted
  = min(requestedFreeVolume,
        remainingPoreCapacity,
        effectiveRate)
```

`SoilLiquidInfiltrationSystem` runs immediately before hydraulic transport for active free-liquid cells:

```text
free liquid touching supporting porous Terrain
        ↓
retain accepted amount in SoilLiquidSystem
        ↓
remove exactly same accepted amount from LiquidSystem
        ↓
excess remains free and may flow/run off
```

The identity is preserved; this is not a Water-only exchange path.

## Surface retention before horizontal runoff

A supporting landscape material may declare generic microtopographic surface-retention capacity.

This reserve is:

- still **free liquid**;
- different from Soil pore retention;
- material-owned and currently liquid-neutral;
- applied to same-level horizontal runoff;
- not applied to vertical falling.

Conceptually, horizontal transfer may spend only the source volume above its supporting-surface reserve.

A future contact-angle/surface-tension model would need explicit physical properties rather than another Water-specific storage subsystem.

## Liquid surfaces and diagnostics

### Surface projection

`LiquidSurfaceLookup` maintains sparse highest-liquid information by XY column. It can return the highest free liquid overall or the highest cell of a requested `LiquidTypeId` without scanning world space.

It owns no quantity.

### Coherent flow diagnostics

A solver step can contain several simultaneous edge transfers through one cell. Diagnostics aggregate signed transfer vectors before choosing a dominant direction.

Example:

```text
400 east + 400 west -> no coherent net direction
500 east + 480 west -> small eastward net direction
```

`LiquidFlowSample` is an observation of the latest authoritative transfer step, not persistent velocity state.

Water presentation may additionally suppress visually insignificant horizontal coherent flux relative to local Water amount while still showing real downward falling. That is presentation-only; it never suppresses solver work.

## Water integration

```text
shared generic owners
  LiquidSystem
  LiquidFlowSystem
  SoilLiquidSystem
  SoilLiquidInfiltrationSystem
        ↓
WaterSystem typed facade
        ├─ precipitation
        ├─ evaporation
        ├─ drinking provider
        ├─ wading constraint
        └─ Water presentation/diagnostics
```

Water-specific semantics do not automatically apply to another liquid merely because it shares generic transport.

## Invariants

- Free-liquid quantity has one authoritative owner.
- One runtime free-liquid world has one shared hydraulic solver.
- Liquid identities are open; generic mechanics contain no concrete-liquid catalog/switch.
- Every transported liquid has explicit transport properties.
- Viscosity affects transfer exactly once through the mobility calculation.
- Current free cells are single-component and unsupported unlike contact blocks deterministically.
- Pure transport conserves exact total volume.
- Retained constituents share one Soil pore capacity.
- Soil infiltration combines independent Terrain permeability and liquid viscosity.
- Surface retention is free-liquid reserve, not Soil moisture.
- Dormant hydraulics schedule no work until a relevant mutation wakes them.
- Read-side surfaces/flow samples never become second authorities.

## Current limitations

Not implemented:

- multi-component free-liquid mixtures;
- miscibility/phase separation;
- density/buoyancy layering;
- pressure/inertia/turbulence/viscous momentum solver;
- surface tension/contact-angle wetting;
- temperature/freezing/boiling;
- chemistry/biological decay;
- diffusion/displacement/leaching between retained constituents;
- arbitrary-liquid precipitation/evaporation/traversal semantics;
- automatic arbitrary Geometry-change displacement/wake coordination.

## Code and tests

Primary runtime code lives in the liquid/Soil mechanic packages and is wired once by `SimulationAssembly`.

Headless tests cover finite mutations, capacity, vertical/horizontal Geometry flow, conservation, deterministic no-mix contact, viscosity-dependent mobility, run-on Soil infiltration, shared pore capacity, surface retention, dormancy/reactivation and multi-cell fixed-point stability.

## Sources

**Internal EvoForge model:** the current local finite-volume head-relaxation solver, active-frontier scheduling, single-component contact rule and surface-retention mechanism are project-specific deterministic mechanics. EvoForge does not claim to implement Navier–Stokes or a published CFD solver.

See [Water](water.md), [Surface Hydrology](hydrology.md), [Soil Hydraulics](soil-hydraulics.md), [Geometry](../foundations/geometry.md), [ADR-007](../../decisions/007-liquid-transport-and-composition-boundary.md), and [References](../../references.md).
