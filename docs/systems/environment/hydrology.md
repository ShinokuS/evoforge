# Surface Hydrology

## In plain language

Surface Hydrology turns **finite Water** into wet Soil, puddles, runoff and evaporation through ordinary runtime physics.

Rain does not paint a wet flag onto Terrain. It contributes real Water. Porous Terrain can retain part of that Water; excess remains free, moves through Geometry and may collect in depressions. Evaporation removes Water that is actually exposed.

```text
atmospheric Water input
      ↓
exposed sky surface
      ↓
Soil retention
      ↓
excess finite free Water
      ↓
generic infiltration + hydraulic flow
      ↓
puddles / runoff / standing Water
      ↓
evaporation
```

There is no `Puddle` object and no special world-generation puddle rule.

## Current status

Implemented runtime capabilities include:

- Water-specific precipitation and evaporation;
- shared vertical sky-surface resolution;
- Terrain-first rainfall infiltration into retained Soil Water;
- run-on infiltration before hydraulic redistribution;
- material-owned surface retention before same-level runoff;
- deterministic finite Water accounting;
- periodic/cyclic precipitation for scenarios;
- sparse Water/Soil candidate iteration;
- finite-world containment through shared Geometry;
- deterministic visual/headless runtime acceptance scenes.

The retired generated climate-normal/rainfall-regime pipeline is no longer production code. Climate-driven Continuum generation will be rebuilt later behind new contracts.

## Ownership

Authoritative mutable state remains separated:

```text
Landscape/Terrain       solid material identity
SoilLiquidSystem        retained constituents in porous Terrain
LiquidSystem            finite free-liquid identity + volume
WaterSystem             typed Water facade over LiquidSystem
```

Read-side surfaces and occupied-cell views are derived indexes, never owners of quantity.

## Shared vertical sky surface

The vertical sky-surface resolver determines what the atmosphere currently reaches in one XY column by comparing exposed Terrain and Water surfaces.

Consequences:

- higher Terrain shields lower Terrain/Water;
- an exposed Water surface can receive atmospheric input/removal;
- an empty column does not create Water merely because precipitation exists.

## Rain onto Terrain

When precipitation reaches exposed Terrain, Water is offered to porous Soil first:

```text
input Water
   ↓
retained Soil Water
   +
remainder
   ↓
free Water in available surface/open volume
   +
unplaced remainder if capacity is unavailable
```

Exact accounting remains:

```text
input = infiltrated + freeSurfaceWater + unplaced
```

Rain onto exposed free Water adds to that Water column instead of inventing a second storage model.

## Run-on infiltration

Free Water that reaches porous Terrain can infiltrate before continuing downstream. The mechanism is generic over liquid identity; atmospheric Water does not create a separate Water-only Soil exchange engine.

## Surface retention before runoff

Terrain may expose a finite surface-retention capability representing microtopographic storage smaller than explicit cell geometry.

That reserve remains free liquid, is separate from Soil pore Water, reduces same-level runoff and does not prevent valid downward movement.

## Hydraulic redistribution

After infiltration, the shared [Liquids](liquids.md) solver redistributes excess free Water using geometry and hydraulic head.

A puddle therefore emerges when:

```text
Water input
- Soil uptake
- outflow
- evaporation
```

leaves finite free Water locally.

## Periodic/cyclic precipitation

Hand-authored/debug scenarios can use deterministic precipitation schedules. They are runtime forcing controls, not generated climate truth. The same production Water/Soil/liquid mechanics process their Water.

## Evaporation

Evaporation removes finite Water from exposed candidates. Free Water and retained Soil Water remain separately accounted; other liquid identities are unaffected by Water-specific evaporation.

## Continuum relationship

Current Continuum code does not yet generate climate, drainage, rivers or lakes and does not drive runtime weather.

Later stages will provide generated environmental facts through explicit contracts:

```text
Continuum terrain + geology
        ↓
climate
        ↓
drainage / rivers / lakes
        ↓
explicit runtime materialization/forcing boundary
        ↓
existing authoritative Water/Soil/liquid systems
```

Generated hydrology must not bypass or duplicate runtime Water ownership. Presentation and page/cache state must not influence physical results.

## Invariants

- Rain/evaporation create or remove finite authoritative Water only through Water/liquid owners.
- Terrain identity and retained Soil Water remain separate facts.
- Rain onto exposed Terrain attempts Soil retention before excess free Water.
- Run-on infiltration uses the shared retained-liquid mechanism.
- Source/sink operations preserve explicit finite accounting.
- Hydraulic redistribution uses the generic liquid solver.
- Atmosphere targets current exposed surfaces.
- Puddles emerge from state, rates and Geometry rather than a content type.

## Current limitations

Not currently implemented as Continuum-generated facts:

- climate fields and seasonality;
- drainage/catchments;
- rivers and generated lakes;
- groundwater/water table;
- erosion/sediment transport;
- plant uptake;
- moving storm fronts and full meteorology.

## Code and tests

Runtime implementation spans the environment precipitation/evaporation/sky code plus the shared liquid and Soil systems. Headless tests protect finite accounting, exposure, infiltration, retention, bounds and traversal. Runtime visual scenarios complement those tests.

## Sources

**Internal EvoForge model.** The precipitation → Soil retention → free flow → evaporation composition is project-specific runtime architecture.

See [Liquids](liquids.md), [Water](water.md), [Soil Hydraulics](soil-hydraulics.md), [World Generation](../world-generation/overview.md), [Continuum Development Plan](../world-generation/continuum-development-plan.md), and [ADR-007](../../decisions/007-liquid-transport-and-composition-boundary.md).
