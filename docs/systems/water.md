# Water

## Purpose

Define Water-specific behavior on top of EvoForge's shared liquid foundations.

Water was the first implemented liquid, so the original storage and hydraulic solver were historically named after Water. Those generic mechanics now live in [Liquids](liquids.md). `WaterSystem` is a narrow typed facade for its open `WaterSystem.TYPE` identity; atmosphere interaction, traversal and presentation remain explicit Water consumers.

## Ownership

Authoritative free-liquid quantity is owned by `LiquidSystem`:

```text
XYZ -> dry
   or
XYZ -> LiquidTypeId + finite free volume
```

Water owns the open liquid identity `water`. `WaterLookup` projects only that identity:

```java
int amount(int x, int y, int z);
```

Water-specific callers therefore do not need generic composition knowledge, while transport is no longer duplicated or hard-wired to Water.

The accepted normalized volume scale is unchanged:

```text
CellVolume.EMPTY = 0
CellVolume.FULL  = 1_000_000
```

## Mutation boundary

`WaterSystem` delegates bounded arithmetic to the shared liquid owner:

```java
int addAtMost(x, y, z, requested);
int removeAtMost(x, y, z, requested);
```

The result is the volume that actually entered or left Water. Geometry capacity, sparse wet/dry state and hydraulic wakeup are owned by the generic liquid foundation.

Water-specific sources and sinks such as precipitation and Water evaporation still go through this facade rather than mutating generic storage directly.

## Shared hydraulic transport

`WaterFlowSystem` is a Water-facing adapter over `LiquidFlowSystem`.

The physical behavior accepted during the Water milestone is preserved:

- Shape-derived free capacity and liquid surface height;
- boundary opening floors and vertical falling;
- deterministic two-phase plan/limit/commit updates;
- exact volume conservation;
- proportional limiting of simultaneous transfers;
- fixed-point relaxation and integer dormancy;
- sparse active frontier;
- latest actual-transfer diagnostics.

The generic solver preserves liquid identity while moving volume. It does not branch on names such as Water, blood or wine.

A multi-liquid runtime composes one shared `LiquidFlowSystem`; `WaterFlowSystem` can wrap that solver to expose Water-only diagnostics rather than creating a second hydraulic authority.

See [Liquids](liquids.md) for the current free-liquid single-component contact rule and future mixture seam.

## Water surface projection

`WaterSurfaceLookup` is a Water-filtered projection over generic free-liquid surfaces. It continues to expose the highest positive-Water Z for each XY column and deterministic wet-column iteration.

The projection owns no liquid quantity. It exists for Water-specific atmosphere/presentation consumers.

## SurfaceWaterStorage

The existing landscape `SurfaceWaterStorage` capability remains Water-specific definition data.

It represents finite free Water retained by a supporting material surface before horizontal runoff. It is not retained Soil pore liquid, is not deleted, and remains part of authoritative free-liquid volume.

The shared solver consumes a typed `LiquidSurfaceRetentionLookup`. `WaterFlowSystem` adapts `SurfaceWaterStorage` into that port only for the Water identity. This leaves room for future liquids to define different surface retention without changing hydraulic Geometry.

As before, the reserve applies only to same-Z horizontal runoff. Vertical falling through a physical opening does not subtract the reserve.

## Soil infiltration and Water moisture

Soil infiltration is no longer a Water-only physical mechanism. `SoilLiquidInfiltrationSystem` can transfer any active free-liquid identity into supporting porous terrain, while `SoilLiquidSystem` owns the retained composition and one shared material pore capacity.

Water participates through the same mechanism:

```text
free Water
    ↓
generic Soil liquid infiltration
    ↓
retained Soil composition
    ↓
SoilMoistureSystem(WATER) projection
```

`SoilMoistureSystem` remains the Water-shaped compatibility capability used by current rain, evaporation, inspector and related consumers. It projects only the configured Water constituent even when the underlying Soil cell also retains another liquid.

The current `WaterSoilExchangeSystem` is only a compatibility adapter for existing Water-oriented runtime wiring; its actual active-liquid infiltration pass delegates to the generic Soil mechanism.

A future liquid/material interaction resolver may give blood, wine or another liquid a different infiltration limit from Water without changing the generic storage model.

## Atmosphere interaction

Current precipitation and evaporation remain Water integrations.

`VerticalSkySurfaceSystem` and the current environment composition still operate on the production Water surface capability:

- rain onto Terrain fills retained Water capacity first and places excess as free Water;
- rain onto exposed Water adds to Water;
- Water evaporation removes exposed free Water before exposed retained Water moisture;
- covered Water is not evaporated merely because it exists in storage.

Retained blood or wine does not automatically evaporate on Water's schedule. When another liquid becomes an atmosphere participant, its source/sink semantics must be introduced explicitly.

See [Surface Hydrology](hydrology.md).

## Water-aware terrestrial traversal

`WaterWadingConstraint` remains Water-specific gameplay/locomotion behavior.

A mover with `WaterWadingProfile(maxDepth)` evaluates current Water depth during path planning and authoritative Movement revalidation. Reusing the same hydraulic solver or Soil-infiltration mechanic for another liquid does not automatically give that liquid Water's wading semantics or hazard behavior.

See [Water Traversal](water-traversal.md).

## Geometry changes

Geometry owns Shape state; liquid systems own quantity. Geometry therefore never silently deletes displaced Water.

`WaterFlowSystem.activateAt(x,y,z)` remains the Water-facing hydraulic wake point for a higher-level coordinator after relevant Geometry changes. General runtime displacement coordination remains future work.

## Mixing boundary

Water can coexist in the same generic liquid world with future liquid identities, but current **free-liquid** cell content is single-component. Unsupported unlike-liquid contact is blocked rather than implicitly merged.

Retained Soil composition may already hold several constituents because they share porous capacity; that does not implement free-liquid mixing, chemistry, reactions or phase separation.

Free-liquid mixing remains a separate design milestone. See [Decision 007](../decisions/007-liquid-transport-and-composition-boundary.md).

## Deliberately absent

Current Water integration does not implement:

- drinking/Thirst interactions;
- shallow-Water speed penalties;
- swimming or waterborne locomotion;
- current forces, knockback or drowning;
- Water-body identity;
- detailed pressure/inertia/viscosity/turbulence/erosion;
- object displacement volume;
- deep groundwater/drainage;
- automatic wake/displacement coordination for arbitrary Geometry mutation;
- generated/streamed world-bound semantics beyond optional explicit runtime bounds;
- arbitrary-liquid atmosphere or traversal semantics;
- free-liquid mixtures/reactions;
- retained-liquid diffusion, leaching or reactions.

## Tests and acceptance

Existing Water headless coverage remains the regression contract for Water behavior: finite add/remove arithmetic, Shape capacity/openings, exact conservation, simultaneous-exit limiting, SurfaceWaterStorage, vertical falling, run-on Soil infiltration, saturated Soil behavior, deterministic dormancy, actual-flow diagnostics, cached Water surfaces, precipitation/evaporation accounting, Water-aware traversal and finite-world containment.

Generic liquid/Soil tests additionally prove that non-Water identities use the same hydraulic solver, can use the same Soil-infiltration mechanism, share one retained pore capacity, preserve their retained identity and cannot accidentally mix in free-liquid storage through overwrite or deterministic iteration order.

Visual Water acceptance remains Rain Cycle, stacked Z flow, Geometry/Ramp stress, Surface optical depth and calm/active Water presentation.

See [Liquids](liquids.md), [Surface Hydrology](hydrology.md), [Geometry and Shape](geometry.md), [Water Traversal](water-traversal.md), and the historical [Water Foundation note](../notes/water-foundation.md).