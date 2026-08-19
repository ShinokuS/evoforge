# Water

## In plain language

Water is a specific liquid identity built on EvoForge's generic liquid mechanics.

The generic foundation owns finite volume, flow, viscosity-aware transport, Soil retention and surface-retention behavior. Water adds **Water-specific meanings**: rain can create it, evaporation can remove it, terrestrial movers may have wading limits, thirsty agents can drink it, and presentation can render/inspect it as Water.

There is no separate Water hydraulic simulator hidden beside the generic liquid solver.

## Current status

Implemented Water-specific integrations include:

- typed free-Water read/mutation facade;
- Water-filtered surface and flow diagnostics;
- precipitation and Water-only evaporation;
- retained Water inside the generic `SoilLiquidSystem`;
- definition-driven Water wading for Movement/MoveTo;
- finite Water drinking as a generic Agent opportunity provider;
- Water presentation/inspection/weather integration;
- generated initial-Water compatibility bootstrap plus the future Stage 7 ordering plan.

Swimming, drowning, currents acting on actors, groundwater and detailed fluid physics are not implemented.

## Ownership

Authoritative free-liquid quantity belongs to `LiquidSystem`:

```text
XYZ -> dry
or
XYZ -> LiquidTypeId + finite volume
```

`WaterSystem` owns/exports the semantic Water identity and delegates typed operations to that generic owner.

```java
int amount(x,y,z)
int addAtMost(x,y,z,requested)
int removeAtMost(x,y,z,requested)
```

The returned mutation amount is the amount actually accepted/removed.

`WaterLookup` and Water surfaces/diagnostics do not own quantity.

## Volume and physical scale

Runtime Water volume uses normalized `CellVolume`:

```text
0         = empty
1_000_000 = one full cell volume
```

That is not automatically litres.

When a mechanic requires real-world volume, `PhysicalSpaceScale` converts normalized cell volume into physical cell dimensions. Current finite Water drinking uses this physical scale so consumed liquid is not interpreted as an arbitrary raw simulation fraction.

## Shared free-liquid flow

Water is registered with the reference kinematic viscosity used by the generic transport model:

```text
νwater = reference viscosity = 1 mm²/s
```

Therefore for Water:

```text
mobility = nominal * νreference / νwater
         = nominal
```

and the previously accepted Water transport cadence remains the reference baseline.

All actual flow is performed by the single shared `LiquidFlowSystem` using Geometry capacity/openings, hydraulic head, deterministic simultaneous transfer limiting, exact conservation and active-frontier dormancy.

`WaterFlowLookup.from(...)` simply filters generic latest-step coherent-flow diagnostics for Water identity.

See [Liquids](liquids.md).

## Water surfaces

`WaterSurfaceLookup` is a Water-filtered projection over generic liquid surfaces. It answers which Water cell is highest in an XY column for atmosphere, presentation and traversal consumers.

It is maintained/read as a derived projection and owns no Water volume.

## Soil-retained Water

Water can leave free-liquid state and become retained inside porous Terrain through the generic Soil-liquid infiltration mechanism:

```text
free Water
    ↓
SoilLiquidInfiltrationSystem
    ↓
SoilLiquidSystem constituent = Water
```

One porous Terrain cell shares its finite pore capacity across all retained constituents.

Retained Water can be queried explicitly as:

```text
soilLiquids.amountOf(WaterSystem.TYPE, x,y,z)
```

There is no separate mutable `SoilMoistureSystem` copy.

For Water's reference viscosity, effective uptake rate equals the Terrain's current reference-liquid permeability.

See [Soil Hydraulics](soil-hydraulics.md).

## Surface retention

Small material-owned microtopographic storage before same-level runoff is generic `SurfaceRetentionDefinitions`, not Water-specific storage.

The retained amount remains **free Water**. It is distinct from Water inside Soil pores.

Valid vertical falling is unaffected.

The former Water-only surface-storage definition path has been removed, preventing two parallel truths for the same behavior.

## Precipitation and evaporation

### Precipitation

When rain hits exposed Terrain:

```text
rain Water
   ↓
porous Soil retention first
   ↓
excess free Water
```

When rain hits an exposed Water surface, it adds directly to the free-Water column.

### Evaporation

Current Water evaporation removes finite exposed Water in this order:

```text
1. exposed free Water
2. exposed retained Soil Water
3. report unfulfilled requested sink
```

It does not evaporate other liquid identities merely because they share generic transport.

See [Surface Hydrology](hydrology.md).

## Water-aware traversal

A mover definition can declare `WaterWadingProfile(maxDepth)`.

The shared production `WaterWadingConstraint` converts current finite Water volume into local depth through neutral Geometry and is used both:

- as advisory MoveTo/path filtering;
- as authoritative Movement start/completion revalidation.

Water does not remove the structural Navigation edge. A different mover may have different locomotion capability over the same geometry.

See [Water Traversal](../traversal/water-traversal.md).

## Finite Water drinking

Water drinking **is implemented** in the current autonomous-agent slice.

It reuses the generic opportunity architecture instead of adding a `DrinkWaterAction` branch inside Agent.

Conceptually:

```text
Perception sees Water cells
      ↓
Water opportunity provider
      ↓
source-neutral OpportunityTarget + valid InteractionSite
      ↓
common Agent Utility competes with food/other opportunities
      ↓
MoveTo reaches site
      ↓
provider-owned timed use
      ↓ completion revalidation
WaterSystem.removeAtMost(...)
      ↓
NeedSystem reduces Thirst proportionally to actual consumed finite Water
```

An undersized puddle can therefore be drained exactly and provides only the benefit corresponding to actual Water removed. The provider does not create infinite “drinkable lake” semantics.

The same source-neutral Agent decision code compares Hunger/food and Thirst/Water opportunities.

See [Autonomous Agents](../agents/agents.md).

## Generated initial Water

Current generated-world compatibility bootstrap can still materialize `WorldAtlas.surfaceHydrology().initialWaterVolumeAt(x,y)` into finite runtime Water one cell above generated Terrain before runtime starts.

However, the **canonical future world-generation sequence** intentionally moves/refines initial Water to Stage 7:

```text
complete dry Terrain/mountains/rivers/lakes/geology/caves/materials
        ↓ Stage 6 acceptance
finite initial Water fill
        ↓ Stage 7
ordinary runtime Water ownership
```

So existing bootstrap infrastructure is reusable but is not evidence that final Stage 7 semantics are complete.

## Geometry mutation boundary

Geometry owns Shape; Water/liquid owns quantity. Changing Shape does not silently delete Water.

The generic liquid solver can be activated explicitly around changed regions, but full automatic displaced-liquid coordination for arbitrary runtime Geometry mutation remains future work.

## Current mixing boundary

Water can coexist in the same world with other liquid identities, but current **free** cells are single-component. Unlike free-liquid contact is explicitly blocked rather than silently mixed.

Retained Soil may hold Water plus other constituents because they share pore capacity. That is finite composition bookkeeping, not implemented chemistry/diffusion.

## Invariants

- Free Water has no storage/solver separate from generic Liquid ownership.
- Water-specific sources/sinks use the typed Water facade.
- Soil-held Water is one retained constituent, not a second moisture authority.
- Generic surface retention is not Water-only state.
- Wading changes mover admissibility, not structural Navigation topology.
- Drinking consumes real finite Water and grants benefit from actual removed volume.
- Other liquid identities do not automatically inherit Water rain/evaporation/drinking/wading/presentation semantics.
- Generated initialization hands Water to runtime ownership exactly once.

## Current limitations

Not implemented:

- swimming/waterborne locomotion;
- shallow-Water speed penalty beyond passability;
- current force/knockback;
- drowning/breathing;
- Water-body identity as an authoritative object;
- detailed pressure/inertia/turbulence/erosion;
- surface tension/contact-angle wetting;
- freezing/boiling/temperature;
- displacement by object body volume;
- deep groundwater/drainage;
- arbitrary-liquid atmosphere/traversal/Need semantics;
- free-liquid mixtures/reactions;
- retained-liquid diffusion/leaching/reactions.

## Code and tests

Water-specific code composes over generic liquid/environment/interaction mechanics. Important coverage includes finite mutation/conservation, precipitation/evaporation, Soil infiltration, surfaces/flow diagnostics, wading start/commit checks, finite drinking/Thirst integration, rain-created puddles and finite-world containment.

Visual acceptance includes Rain Cycle, Geometry/Ramp Water stress, calm/active Water presentation and living Cow scenarios that drink ordinary finite Water.

## Sources

**Internal EvoForge design.** Water-specific atmosphere, wading and finite-drinking integration are project mechanics built over the generic liquid foundation; no external hydrodynamic/animal-behavior solver is claimed.

See [Liquids](liquids.md), [Surface Hydrology](hydrology.md), [Water Traversal](../traversal/water-traversal.md), [Autonomous Agents](../agents/agents.md), [ADR-007](../../decisions/007-liquid-transport-and-composition-boundary.md), and the historical [Water Foundation design record](../../journal/design/water-foundation.md).
