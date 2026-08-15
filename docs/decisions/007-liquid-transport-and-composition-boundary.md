# Decision 007 — Liquid transport and composition boundary

**Status:** Accepted

## Problem

The first liquid implementation was built as Water because Water was the first real consumer. Its finite-volume flow, Geometry interaction, sparse activity frontier, conservation rules and infiltration into porous terrain are not intrinsically Water-specific.

Leaving those mechanics under Water would force future liquids such as blood or wine either to duplicate physical systems or to pretend they are Water. At the same time, introducing a complete free-liquid mixture/chemistry model before a real mixing milestone would create speculative APIs whose semantics are not yet known.

## Decision

EvoForge separates **generic liquid state/transport**, **generic retained Soil composition**, and **liquid-specific gameplay/environment integrations**.

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

One free-liquid world has one authoritative `LiquidSystem` and one shared `LiquidFlowSystem`. Typed liquid integrations may adapt/filter that shared owner, but they must not create independent transport authorities over the same state.

The current **free-liquid** representation intentionally permits one liquid type per occupied cell. Different free liquids do not silently overwrite, coalesce or mix. Unlike occupied cells block cross-contact transfer, and simultaneous unlike inflows into one dry destination are all suppressed so deterministic ordering cannot become an accidental mixing rule.

This free-liquid contact rule is a temporary explicit invariant, not the intended final model of mixing.

## Generic Soil retention

Soil infiltration is generic.

`SoilLiquidSystem` owns liquid retained inside porous terrain. Retained Soil state may contain several liquid constituents at the same terrain cell today because they compete for one material-owned pore capacity rather than occupying free world-cell volume.

```text
Soil capacity = 100
Water retained = 60
Blood retained = 25
remaining pore capacity = 15
```

This retained composition does **not** mean free-liquid mixing or chemistry is implemented. It records constituent quantities sharing a porous medium only.

`SoilLiquidInfiltrationSystem` transfers active free liquid into supporting Soil and removes exactly the amount accepted from free-liquid authority. Excess remains free. Therefore a sufficiently large blood spill can saturate Soil and naturally leave a free puddle without a battle-specific rule.

`SoilHydrology` keeps the effective local material capacity and default infiltration limit. `SoilLiquidInteractionLookup` is the narrow seam for liquid/material-specific uptake. Its default preserves the material limit; future definition-backed interaction data may vary uptake by liquid identity without adding concrete-liquid switches to generic Soil code.

Existing `SoilMoistureSystem` is a typed compatibility projection over retained composition for Water-oriented hydrology/presentation consumers.

## Composition seam

A future free-liquid mixing milestone may replace the single-component free-cell content with a mixture representation and define contact resolution, constituent transfer, miscibility, reactions and derived physical properties.

That work should remain localized around free-liquid content/contact semantics. Geometry transport should continue asking objective questions such as total free volume, surface height, openings and transfer capacity rather than requiring one solver per liquid.

Retained Soil composition already provides a natural future attachment point for contamination, dilution, leaching or reactions inside porous media, but none of those processes are part of this decision.

## Liquid identity

`LiquidTypeId` is a stable open identifier. Concrete identities are domain-owned rather than listed in a central enum/catalog.

The foundation does not introduce a large `LiquidDefinition` property bag for hypothetical viscosity, density, flammability, toxicity or chemistry. The first real mechanic that needs a property should introduce a narrow typed definition capability.

## Surface retention

Free-liquid horizontal runoff may consult `LiquidSurfaceRetentionLookup(type, position)`. The type is part of the query because supporting materials may eventually retain different free-liquid films differently. Current Water hydrology adapts existing `SurfaceWaterStorage` into this generic port.

Surface-film retention is distinct from retained Soil pore composition.

## Consequences

- one deterministic free-liquid transport implementation serves arbitrary liquid identities;
- Soil infiltration is reusable by arbitrary liquids rather than belonging to Water;
- several retained constituents share one Soil capacity instead of each receiving a separate capacity;
- Water remains a semantic integration for precipitation, Water evaporation, wading and presentation;
- future liquids can differ in Soil uptake through a narrow interaction resolver;
- enough incoming liquid naturally leaves free excess after Soil saturation/rate limits;
- free-liquid mixing cannot happen accidentally through storage overwrite or solver ordering;
- future free-mixture and retained-contamination work both have explicit replacement/extension seams;
- unsupported physical/chemical properties are not invented prematurely.

## Deliberately deferred

This decision does not define:

- multi-component **free-liquid** mixtures or solution ratios;
- miscibility/immiscibility and phase separation;
- density layering or buoyancy between free liquids;
- viscosity-dependent hydraulic flow rates;
- temperature, freezing, boiling or other phase changes;
- chemical reactions or biological decay;
- diffusion, displacement or leaching between retained Soil constituents;
- generic precipitation/evaporation for arbitrary liquids;
- generic traversal hazards or presentation for arbitrary liquids.

Those belong to future milestones with real consumers and acceptance criteria.
