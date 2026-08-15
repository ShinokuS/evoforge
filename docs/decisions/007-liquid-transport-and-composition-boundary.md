# Decision 007 — Liquid transport and composition boundary

**Status:** Accepted

## Problem

The first free-liquid implementation was built as Water because Water was the first real consumer. Its finite-volume flow, Geometry interaction, sparse storage frontier and conservation rules are not intrinsically Water-specific, however. Leaving those mechanics under Water would force future liquids such as blood or wine either to duplicate the solver or to pretend they are Water.

At the same time, introducing a full mixture/chemistry model before any real mixing consumer exists would create speculative state and APIs whose required semantics are not yet known.

## Decision

EvoForge separates **free-liquid transport** from **liquid-specific interactions and composition**.

```text
shared free-liquid foundation
  identity + finite volume + Geometry + flow + dormancy
                         |
              typed integration layers
                 /       |       \
              Water    future    future
              rules    liquid    liquid
```

The shared `world.landscape.liquid` package owns generic free-liquid quantity and deterministic transport. `WaterSystem` is a typed facade for the built-in `water` identity and Water-specific systems keep their own responsibilities: Soil infiltration, precipitation/evaporation semantics, terrestrial wading and Water presentation.

One free-liquid world has one authoritative `LiquidSystem` and one shared `LiquidFlowSystem`. Typed liquid integrations may adapt that shared owner for domain-specific consumers, but they must not create independent transport solvers over the same free-liquid state.

The current storage model intentionally permits **one liquid type per occupied cell**. A different incoming type is not silently overwritten, coalesced or converted. If unlike liquids meet, transfer across the contact is blocked. If unlike liquids simultaneously target the same dry cell, all contested inflows are blocked rather than allowing iteration order to choose a winner.

This contact rule is a temporary explicit invariant, not the intended final model of mixing.

## Composition seam

A future mixing milestone may replace the single-component cell content with a composition/mixture representation and define contact resolution, proportional transfer, reactions and derived properties. That work should be localized around liquid content/contact semantics.

The hydraulic Geometry algorithm should continue to ask objective questions such as total free volume, surface height, openings and transfer capacity. It must not need a separate copy for Water, blood, wine or every future reagent.

## Liquid identity

`LiquidTypeId` is a stable open identifier. The foundation does not yet introduce a large `LiquidDefinition` schema because there is no current consumer for speculative fields such as viscosity, density, flammability, temperature response, toxicity or chemistry.

When the first mechanic needs one of those properties, it should become a typed definition aspect/capability rather than a switch on liquid names.

## Surface retention

The generic solver accepts `LiquidSurfaceRetentionLookup(type, position)`. The lookup is typed deliberately: a supporting material may eventually retain different liquids differently. Current Water hydrology adapts the existing `SurfaceWaterStorage` capability into this generic port.

## Consequences

- one deterministic transport implementation serves arbitrary liquid identities;
- Water remains a semantic integration rather than becoming the definition of all liquids;
- existing Water behavior and acceptance scenarios remain valid;
- future liquids can reuse sparse storage, Geometry capacity, flow conservation and dormancy;
- typed liquid facades cannot accidentally fork hydraulic authority;
- mixing cannot happen accidentally through map overwrite or solver ordering;
- a future composition model has a clear replacement boundary;
- unsupported physical/chemical properties are not invented prematurely.

## Deliberately deferred

This decision does not define:

- multi-component mixtures or solution ratios;
- miscibility/immiscibility and phase separation;
- density layering or buoyancy between liquids;
- viscosity-dependent flow rates;
- temperature, freezing, boiling or phase changes;
- reactions, contaminants or reagent effects;
- generic Soil absorption of arbitrary liquids;
- generic precipitation/evaporation for arbitrary liquids;
- generic traversal hazards or presentation for arbitrary liquids.

Those belong to their first real consuming milestone.
