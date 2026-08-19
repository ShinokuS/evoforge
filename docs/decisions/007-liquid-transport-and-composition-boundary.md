# ADR-007: Liquid transport and composition boundary

- Status: Accepted
- Scope: Liquids and porous Terrain
- Decision: One generic free-liquid owner/solver handles registered liquid identities; retained Soil composition is a separate generic owner; Water adds typed integrations without duplicating transport.

## Context

Water was the first liquid consumer, but finite-volume transport, Geometry interaction, conservation and porous-Terrain retention are not inherently Water-specific. Keeping those mechanics inside Water would force future liquids either to duplicate physics or pretend to be Water. A generic liquid×material identity-pair table would only move hard-coding elsewhere, while a full mixture/chemistry model would be premature.

## Decision

The accepted ownership is:

```text
LiquidSystem + LiquidFlowSystem
        │
        ├─ SoilLiquidInfiltrationSystem -> SoilLiquidSystem
        └─ typed integrations such as WaterSystem
```

One runtime free-liquid world has one authoritative storage/solver. Current free-liquid cells contain at most one `LiquidTypeId`; unlike occupied contact is blocked, and contested unlike inflows into one dry destination are suppressed for that solve so iteration order cannot invent mixing.

Every transported liquid has explicit `LiquidTransportProperties`. Current transport property is kinematic viscosity, with deterministic inverse-viscosity mobility:

```text
mobility = nominal * referenceViscosity / liquidViscosity
```

Water uses the reference `1 mm²/s` value. Viscosity is applied once to physical transfer planning.

Retained constituents share one material-owned Soil pore capacity. Effective porous uptake combines Terrain permeability and incoming-liquid viscosity. Surface retention is a separate generic material capability that limits same-level runoff but not vertical falling.

## Why

The boundary generalizes the physics that is already genuinely shared while leaving unproven free-mixture/chemistry semantics explicit and deferred.

## Consequences

- New registered liquid identities reuse one deterministic transport owner.
- Water keeps explicit precipitation/evaporation/wading/drinking/presentation semantics without owning generic flow.
- Material permeability and liquid viscosity remain independently authored/calibrated facts.
- Several retained constituents can occupy one Soil pore volume.
- Future mixture/contact physics has a localized replacement seam.

## Alternatives considered

A Water-only transport engine was rejected as duplicated future physics. Liquid/material identity-pair tables were rejected because they encode content combinations instead of causal properties. A speculative universal mixture/chemistry model was deferred until a real milestone needs miscibility, density, reactions or phase separation.

## Current implementation

`LiquidSystem`, `LiquidFlowSystem`, `LiquidTransportDefinitions`, `SoilLiquidSystem`, `SoilLiquidInfiltrationSystem` and generic `SurfaceRetentionDefinitions` implement this split. `WaterSystem` is a typed facade/integration over shared liquid truth. The former Water-only Soil/surface-storage parallel paths are not production authorities.

## Related documentation

- [Liquids](../systems/environment/liquids.md)
- [Water](../systems/environment/water.md)
- [Surface Hydrology](../systems/environment/hydrology.md)
- [Soil Hydraulics](../systems/environment/soil-hydraulics.md)
