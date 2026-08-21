# Soil Hydraulics

## In plain language

Soil Hydraulics describes how solid Terrain can retain liquid and how easily liquid can enter porous material. Runtime Soil owns changing retained liquid; free liquid remains owned by the liquid system.

The retired V12–V15 generator previously contained generated Soil calibration/formation stages. Those stages are no longer part of the production world-generation baseline.

## Current status

The current runtime ownership is intentionally simpler:

```text
Terrain/material properties
        ↓
SoilProperties lookup
        ↓
SoilLiquidInfiltrationSystem
        ↓
SoilLiquidSystem
  retained constituents

LiquidSystem
  free liquid
```

Runtime systems do not need to know how a future Continuum generator produced Soil facts.

## Separation of facts

A coordinate may simultaneously have:

- solid Terrain/material identity;
- porous Soil capacity/permeability properties;
- retained Soil-liquid composition;
- free liquid above/within available geometry.

These remain separate authoritative facts. A Terrain cell does not become a special "wet Terrain" record when Water is present.

## Runtime infiltration

Free-liquid flow may transfer liquid into porous Terrain through the production infiltration path. Capacity and permeability come from Terrain/Soil properties; mutable retained quantity belongs to `SoilLiquidSystem`.

Liquid identity/transport properties and Terrain porous properties remain separate inputs. Generic code must not hard-code material-name rules such as `granite -> impermeable` or `sand -> fast infiltration` without explicit semantic properties.

## Continuum relationship

Future world generation will eventually produce Soil from explicit causes such as geology, terrain shape, water and climate. That belongs to the Soil generation stage of the Continuum roadmap, not to runtime infiltration.

The intended boundary is:

```text
Continuum geology + terrain + water + climate
        ↓
generated Soil facts
        ↓
explicit runtime materialization/transfer
        ↓
ordinary Soil properties + SoilLiquidSystem
```

Human-authored generation controls remain semantic and normalized. Physical/runtime coefficients are compiled or calibrated outside authored semantic Definitions.

## Invariants

- Free liquid and retained Soil liquid have separate authoritative owners.
- Runtime Soil does not rerun world generation every tick.
- Material identity does not by itself imply hidden hydraulic behavior.
- Cross-domain behavior consumes explicit properties/capabilities rather than switching on material names.
- Future Continuum Soil generation must remain deterministic and replaceable behind its own contract.

## Current limitations

The current Continuum baseline does not yet generate Soil, soil horizons, pedogenesis, groundwater or climate-driven Soil development. Those are later roadmap stages.

## Code and tests

Runtime Soil/liquid implementation lives in the simulation environment/liquid/soil systems. Existing tests protect capacity, infiltration, retention and Water/Soil ownership behavior.

## Sources

**Internal EvoForge design.** Runtime Soil ownership and the generation/runtime boundary are project architecture.

See [Liquids](liquids.md), [Surface Hydrology](hydrology.md), [World Generation](../world-generation/overview.md), [Continuum Development Plan](../world-generation/continuum-development-plan.md), [ADR-007](../../decisions/007-liquid-transport-and-composition-boundary.md), and [ADR-024](../../decisions/024-continuum-large-world-architecture.md).
