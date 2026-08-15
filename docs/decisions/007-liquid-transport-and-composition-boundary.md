# Decision 007 — Liquid transport and composition boundary

**Status:** Accepted

## Problem

The first liquid implementation was built as Water because Water was the first real consumer. Finite-volume flow, Geometry interaction, sparse hydraulic activity, conservation and porous-terrain retention are not intrinsically Water-specific.

Leaving those mechanics under Water would force future liquids such as blood or wine either to duplicate physical systems or to pretend they are Water. At the same time, implementing a complete mixture/chemistry model before a real mixing milestone would create speculative semantics.

The refactor also exposed a second risk: genericization by identity-pair tables. Encoding every liquid/material combination directly would move hardcoding rather than remove it. Transport differences should come from independent physical properties where those properties already have a real consumer.

## Decision

EvoForge separates **generic free-liquid state/transport**, **generic retained Soil composition**, and **liquid-specific integrations**.

```text
free-liquid world
  LiquidSystem + LiquidFlowSystem
          |
          +----> SoilLiquidInfiltrationSystem
          |             |
          |             v
          |       SoilLiquidSystem
          |       shared pore capacity
          |       multi-constituent retained state
          |
          `----> typed integrations
                    Water / future liquids
```

One free-liquid world has one authoritative `LiquidSystem` and one shared `LiquidFlowSystem`. Typed integrations may filter or adapt that state but must not create parallel transport authorities over the same world.

Current free-liquid cells intentionally contain at most one liquid type. Unlike occupied cells reject cross-contact transfer. If unlike sources simultaneously target one dry destination, all contested inflows are suppressed for that solve so deterministic ordering cannot become an accidental mixing rule.

This is a temporary explicit free-composition boundary, not the final physical model of mixing.

## Explicit liquid transport properties

A liquid that participates in transport must have `LiquidTransportProperties` registered for its `LiquidTypeId`.

The first supported physical property is **kinematic viscosity**. It exists because both hydraulic mobility and porous uptake already consume it.

The solver uses deterministic inverse-viscosity scaling relative to a reference liquid:

```text
mobility(nominal, liquid)
    = nominal * referenceViscosity / liquidViscosity
```

Water is registered with the reference `1 mm²/s` value so the accepted Water behavior remains the calibration point.

Viscosity is applied once to the planned hydraulic edge transfer. Aggregate outgoing limiting remains a numerical stability/conservation constraint, not a second viscosity term.

Missing transport properties are a configuration error. Generic mechanics do not silently assign Water-like behavior to an unknown liquid.

This does not create a giant `LiquidDefinition` property bag. Future properties such as density, surface tension or thermal phase data are added only when a concrete mechanic requires them.

## Generic Soil retention

`SoilLiquidSystem` owns liquid retained inside porous terrain. Several constituents may occupy the same Soil cell while competing for one material-owned pore capacity.

```text
Soil capacity  = 100
Water retained = 60
Blood retained = 25
remaining      = 15
```

Retained composition is porous occupancy bookkeeping, not free-liquid mixing or chemistry.

Porous terrain is described by `SoilProperties`:

```text
capacity      -> total pore volume shared by retained constituents
permeability  -> nominal per-tick uptake for the reference-viscosity liquid
```

`SoilLiquidSystem` combines material permeability with the incoming liquid's viscosity through the same deterministic transport math. Accepted infiltration is bounded by requested volume, remaining shared capacity and effective permeability.

This replaces the old Water-only `SoilHydrology` / `SoilMoistureSystem` model and removes the speculative `SoilLiquidInteractionLookup` identity-pair seam. Material and liquid properties remain independently owned.

`SoilLiquidInfiltrationSystem` transfers active free liquid into supporting Soil before the hydraulic solve and removes exactly the accepted amount from free-liquid authority. Excess remains free.

No Water-only Soil exchange compatibility adapter remains in production composition.

## Surface retention

Microtopographic free-liquid retention is a separate material capability from Soil pore retention.

`SurfaceRetentionDefinitions` and `TerrainSurfaceRetentionLookup` provide a generic material-owned capacity consulted before same-Z horizontal runoff. The query is position/material based, not a liquid/material identity table.

The former Water-only `SurfaceWaterStorageDefinitions`, `SurfaceWaterStorageLookup` and `TerrainSurfaceWaterStorageLookup` are removed. There is one surface-retention path.

If future wetting physics requires liquid dependence, the model should add the relevant physical capability (for example surface tension/contact angle) rather than restore concrete identity switches.

Vertical falling is not reduced by surface retention.

## Water integration

`WaterSystem` owns the Water identity and a typed facade over the shared `LiquidSystem`. It does not own separate storage or transport.

Water-specific behavior remains explicit:

- precipitation source semantics;
- Water evaporation semantics;
- Water wading/traversal rules;
- Water presentation and filtered diagnostics.

`WaterFlowLookup.from(...)` filters the shared generic flow diagnostics by Water identity. There is no Water transport solver beside `LiquidFlowSystem`.

## Composition seam

A future free-liquid mixing milestone may replace the single-component free-cell content representation and define constituent transfer, miscibility, phase separation, density effects, reactions and derived mixture properties.

That work should remain localized around content/contact/physical-property semantics rather than replacing Geometry transport with one solver per concrete liquid.

Retained Soil composition already provides an attachment point for future contamination, dilution, displacement, leaching or reactions inside porous media, but none of those processes are implied by retained constituent bookkeeping.

## Liquid identity

`LiquidTypeId` is a stable open identifier. Concrete identities are domain-owned instead of being listed in a central enum/catalog.

Generic code may require explicit typed capabilities for a liquid (currently transport properties), but it must not branch on names such as `water`, `blood` or `wine`.

## Consequences

- one deterministic free-liquid owner and solver serve arbitrary registered liquid identities;
- Water behavior remains explicit without owning generic physics;
- transport differences already required by mechanics are data-driven through kinematic viscosity;
- viscosity is applied once, keeping the physical model inspectable;
- Soil capacity/permeability belong to material definitions;
- all retained constituents share one Soil pore capacity;
- liquid-specific Soil uptake emerges from permeability × viscosity rather than identity pair tables;
- enough incoming liquid naturally leaves free excess after rate/capacity limits;
- surface retention is one generic material capability rather than a Water parallel hierarchy;
- free-liquid mixing cannot happen accidentally through overwrite or iteration order;
- future mixture/chemistry work has explicit extension/replacement seams without speculative current APIs.

## Deliberately deferred

This decision does not define:

- multi-component free-liquid mixtures or solution ratios;
- miscibility/immiscibility and phase separation;
- density layering or buoyancy;
- surface tension/contact-angle wetting;
- temperature, freezing, boiling or other phase changes;
- chemical reactions or biological decay;
- diffusion, displacement or leaching between retained Soil constituents;
- generic precipitation/evaporation for arbitrary liquids;
- generic traversal hazards or presentation for arbitrary liquids.

Kinematic viscosity is intentionally **not deferred** because current transport and infiltration already consume it.
