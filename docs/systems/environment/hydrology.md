# Surface Hydrology

## In plain language

Surface Hydrology is the part of EvoForge that turns **finite rain into wet Soil, puddles, runoff and later evaporation**.

Rain does not paint a “wet” flag onto Terrain. It contributes real Water. Porous Terrain can retain part of that Water inside its finite pore space; any excess remains free, can move through Geometry, collect in depressions or run downhill. Evaporation later removes only Water that is actually exposed to the sky.

The runtime Water cycle therefore emerges from several owners working together:

```text
atmospheric Water input
      ↓
exposed sky surface
      ↓
Soil retention first when Terrain is hit
      ↓
excess finite free Water
      ↓
generic liquid infiltration + hydraulic flow
      ↓
puddles / runoff / standing Water
      ↓
finite Water evaporation
```

There is no `Puddle` object or `if raining -> make puddle` rule.

## Current status

Implemented now:

- Water-specific precipitation and evaporation;
- one shared vertical sky-surface resolver;
- Terrain-first rainfall infiltration into retained Soil Water;
- generic run-on free-liquid infiltration before hydraulic flow;
- material-owned surface retention before same-level runoff;
- deterministic finite Water accounting;
- periodic/cyclic rain schedules for scenarios;
- generated climate-normal Water forcing;
- rainfall-regime preparation/runtime pulse compilation;
- sparse Water/Soil candidate iteration instead of world-wide scans;
- finite-world containment through shared Geometry;
- deterministic visual/headless acceptance scenes.

Full meteorology, groundwater, erosion and plant uptake are not part of the current model.

## Ownership

Authoritative mutable state stays separated:

```text
Landscape/Terrain       solid material identity
SoilLiquidSystem        retained constituent composition in porous Terrain
LiquidSystem            finite free-liquid identity + volume
WaterSystem             typed Water facade over LiquidSystem
```

Derived/read-side projections include Terrain surfaces, generic liquid surfaces, Water-filtered surfaces and retained-liquid occupied cells. They are indexes/views, never owners of quantity.

There is no second authoritative `SoilMoistureSystem`: retained Water is simply the Water constituent in `SoilLiquidSystem`.

## Shared vertical sky surface

`VerticalSkySurfaceSystem` determines what the atmosphere can currently hit in one XY column.

Conceptually it compares the highest relevant Terrain and Water surfaces:

```text
higher exposed Water  -> atmosphere sees Water
higher exposed Terrain -> atmosphere sees Terrain
no surface             -> no hydrology target
```

Current coarse-cell tie semantics are Terrain-first.

Consequences:

- higher Terrain shields lower Terrain/Water;
- a lake surface above Terrain receives rain/evaporation at the Water surface;
- a Water-only column remains addressable;
- an empty column does not spontaneously create Water merely because rain exists.

This is a Water-oriented sky contract today; arbitrary-liquid atmosphere participation needs explicit future semantics.

## Rain onto Terrain

When precipitation hits exposed Terrain, current Water is offered to porous Soil first:

```text
input Water
   ↓
SoilLiquidSystem.infiltrateAtMost(WATER, ...)
   ↓
retained Water
   +
remainder
   ↓
free Water placed into available surface/open volume
   +
unplaced remainder if no capacity exists
```

Exact accounting:

```text
input = infiltrated + freeSurfaceWater + unplaced
```

A non-porous material has no Soil capacity, so the full placeable amount remains free.

Rain onto already exposed free Water bypasses Soil at that surface and adds to the Water column.

## Soil uptake rate

Current porous Terrain supplies:

```text
capacity      total shared retained-liquid pore volume
permeability  nominal reference-viscosity uptake per simulation tick
```

For liquid viscosity `ν` and reference viscosity `νref`:

```text
effectiveRate = permeability * νref / ν
```

For Water, production uses the reference viscosity, so the effective rate equals the configured nominal permeability.

One infiltration operation is bounded by:

```text
accepted = min(requested,
               remainingPoreCapacity,
               effectiveRate)
```

Generated worlds can supply spatially varying physical Soil profiles prepared from morphology/drainage/semantic Soil instead of old coordinate-noise variation. See [Soil Hydraulics](soil-hydraulics.md).

## Run-on infiltration

Rainfall is not the only way free Water enters Soil.

Immediately before an active hydraulic solve:

```text
LiquidFlowProcess wakes
      ↓
SoilLiquidInfiltrationSystem.update()
      ↓
for active free-liquid contacts:
    retain what Soil can accept
    remove exactly that amount from free LiquidSystem
      ↓
LiquidFlowSystem.update()
```

This means Water running onto porous Terrain can infiltrate before continuing downstream.

The mechanism is generic over liquid identity. Water-specific atmosphere does not create a Water-only Soil exchange engine.

## Surface retention before runoff

Supporting Terrain may declare a finite `SurfaceRetentionDefinitions` capacity: a project-level representation of small-scale microtopographic storage that is too fine to model explicitly in cell geometry.

This reserve:

- remains free-liquid quantity;
- is separate from Soil pore Water;
- is currently liquid-neutral;
- reduces same-Z horizontal runoff only;
- does not prevent valid downward falling.

A source with several horizontal exits shares one reserve through aggregate limiting, so the reserve is not accidentally applied once per neighbor.

## Hydraulic redistribution

After infiltration, the shared generic [Liquids](liquids.md) solver redistributes excess free Water using Geometry capacity/openings and hydraulic head.

A puddle therefore appears when the local combination of:

```text
rain input
- Soil uptake
- horizontal/vertical outflow
- evaporation
```

leaves finite free Water at a location.

No separate puddle generator is involved.

## Periodic/cyclic precipitation

Hand-authored/debug scenarios can use `PrecipitationSchedule` to produce deterministic pulses or active windows.

These are scenario/runtime forcing controls, not global climate truth. The same Water/Soil/liquid mechanics process the resulting Water.

The Rain Cycle scenario deliberately uses this path so the causal sequence can be watched and inspected.

## Evaporation

Current evaporation is a finite **absolute Water removal** per exposed wet XY candidate, not percentage decay.

Candidates come from sparse Water surfaces and retained-Soil cells containing Water, avoiding scans of all world coordinates.

Removal order:

```text
1. exposed free Water
2. exposed retained Soil Water
3. any requested remainder stays unfulfilled
```

Exact accounting:

```text
requested
  = freeWaterRemoved
  + retainedWaterRemoved
  + unfulfilled
```

Other retained liquid identities are not removed by Water evaporation.

For the older periodic scenario forcing, evaporation is suppressed on a tick where precipitation occurs so the result does not depend on scheduler handler order.

## Generated climate-normal forcing

Generated worlds also have a climate-normal path that translates immutable physical climate rates into the same runtime Water/Soil mechanics.

For an exact rational `CellVolumeRate = p/q`, the amount assigned to positive absolute tick `t` is:

```text
amount(t) = floor(p*t/q) - floor(p*(t-1)/q)
```

Therefore cumulative amount over ticks `1..T` is exactly:

```text
floor(p*T/q)
```

No mutable fractional carry is needed.

Current generated baseline interval semantics evaluate potential evaporation against state that existed at the start of the interval and then add precipitation at the interval boundary. Newly generated rain is therefore not immediately removed by that same generated baseline tick.

This convention is distinct from the periodic-scenario “skip evaporation on rain tick” rule, even though both avoid accidental same-tick loss.

Large physical rates are applied through repeated bounded `CellVolume.FULL` calls while re-resolving the exposed surface as Water rises.

## Rainfall regime preparation

Long-term precipitation amount alone does not say how rain is distributed in time. EvoForge therefore has a separate [Rainfall Regime Calibration](rainfall-calibration.md) path combining:

```text
long-term physical precipitation rate
+
mean dry/wet spell statistics
      ↓
RainfallRegime
      ↓
algorithm-specific runtime pulse compiler
```

That model is preparation/runtime atmosphere policy; it does not replace Water/Soil ownership.

## Optional finite world bounds

When `WorldBounds` are configured, out-of-bounds space appears as closed `FullShape` through shared Geometry. Free Water therefore cannot leak through a special hidden map-edge rule.

Without bounds, current unbounded-coordinate semantics remain possible.

## Invariants

- Rain/evaporation create/remove finite authoritative Water only through Water/liquid owners.
- Terrain identity and Soil retained Water remain separate facts.
- Rain onto exposed Terrain attempts Soil retention before creating excess free Water.
- Run-on infiltration uses the same generic retained-liquid mechanism.
- All source/sink operations have exact finite accounting.
- Surface retention is free Water/liquid, not Soil pore Water.
- Hydraulic redistribution uses the generic liquid solver.
- Atmosphere targets current exposed surfaces rather than arbitrary underlying cells.
- Scheduling/order must not change physical Water accounting.
- Puddles emerge from state/rates/geometry rather than a puddle content type.

## Current limitations

Not implemented:

- moving storm fronts/spatial weather fields in the general runtime;
- humidity, wind or radiation-driven evaporation;
- canopy/object atmospheric shielding;
- deep drainage/groundwater/water table;
- plant root uptake;
- erosion/sediment transport;
- pressure/inertia/turbulence;
- freezing/boiling;
- arbitrary-liquid atmosphere behavior;
- chemistry/mixing;
- automatic hydraulic response to every possible runtime Geometry mutation.

## Code and tests

Primary code spans:

```text
world/environment/precipitation/
world/environment/evaporation/
world/environment/sky/
world/environment/atmosphere/
liquid/Soil mechanics
world/climate/
world/calibration/rainfall/
```

Headless coverage checks finite precipitation/evaporation accounting, sky exposure, Soil infiltration/saturation, run-on uptake, surface retention, dormancy, exact rational climate forcing, bounds and Water traversal. Manual Rain Cycle / causal Soil acceptance supplements numeric tests where visible behavior matters.

## Sources

**Internal EvoForge model:** the runtime precipitation → Soil retention → free-flow → evaporation composition and finite-volume hydrology mechanics are project-specific.

**Statistical weather-model context:** stochastic precipitation literature commonly models wet/dry occurrence separately from positive rainfall amount; Katz (1977) and Richardson (1981) are useful lineage for that separation. EvoForge's current alternating mean-preserving pulse compiler is much simpler and is not a direct implementation of their full stochastic generators.

See [References](../../references.md), [Liquids](liquids.md), [Water](water.md), [Soil Hydraulics](soil-hydraulics.md), [Rainfall Regime Calibration](rainfall-calibration.md), and [ADR-007](../../decisions/007-liquid-transport-and-composition-boundary.md).
