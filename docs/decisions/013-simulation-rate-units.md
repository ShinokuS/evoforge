# ADR-013: Environmental rates use explicit exact dimensions

- Status: Superseded
- Scope: Environmental rate representation
- Decision: The original exact `CellVolumeRate`-per-simulation-tick climate representation has been superseded for generated physical climate by explicit physical depth/time plus `PhysicalSpaceScale` and `SimulationTimeScale`; exact rational simulation rates remain valid where they are still the owning runtime/scenario representation.

## Context

The original climate slice needed exact long-term finite-volume rates without inventing an arbitrary “per N ticks” reference period or assigning a real-world duration to every existing simulation tick prematurely.

## Decision

The historical decision introduced reduced non-negative rational `CellVolumeRate` values:

```text
volumeUnitsNumerator / tickDenominator
```

and derived exact long-run rates from operational precipitation/evaporation schedules. For cyclic precipitation the mean was:

```text
amountPerPulse * floor(activeTicks / intervalTicks) / cycleTicks
```

At that time the simulation tick was the only explicit time dimension and no physical tick duration was assumed.

The project later gained an explicit physical-space/time calibration boundary for generated environmental physics. Generated climate now uses physical `WaterDepthRate`/`ClimateSpec` facts and requires explicit `PhysicalSpaceScale` + `SimulationTimeScale` when converting to runtime cell-volume/tick quantities.

## Why

The original exact-rational principle prevented hidden arbitrary cadence. The later physical-scale model became necessary once generated Soil/climate algorithms genuinely needed real dimensions. Superseding the old universal representation is safer than pretending a tick-only rate still represents all climate semantics.

## Consequences

- Existing exact rational simulation-rate types remain useful for mechanics/scenario schedules that are explicitly expressed in simulation units.
- Generated physical climate uses physical dimensions and an explicit conversion boundary.
- UI/render speed remains unrelated to authoritative simulation time.
- No implicit physical duration is assigned when the owning subsystem has not declared one.

## Alternatives considered

Floating-point long-term rates and arbitrary fixed “per N ticks” reference periods were rejected. Declaring one universal real-world duration for every tick before a consumer required physical scale was also rejected.

## Current implementation

`CellVolumeRate` still exists for simulation-unit rate contracts. Generated-world physical climate/Soil paths instead use `WaterDepthRate`, `PhysicalSpaceScale` and `SimulationTimeScale`, with conversion performed before runtime where required. Current generated-world docs and ADR-021 describe the newer canonical preparation/calibration boundary.

## Related documentation

- [Time and Scheduling](../systems/foundations/time.md)
- [Surface Hydrology](../systems/environment/hydrology.md)
- [Soil Hydraulics](../systems/environment/soil-hydraulics.md)
- [ADR-021](021-world-preparation-and-calibration-boundary.md)
