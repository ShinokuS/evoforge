# ADR-013: Environmental rates use explicit dimensions

- Status: Accepted
- Scope: Environmental rate representation
- Decision: A rate must state the dimensions it belongs to. Runtime simulation-unit rates may use exact rational cell-volume/tick values; any conversion from physical world units requires explicit space and time scales rather than an implicit tick duration or cell size.

## Context

Environmental mechanics need exact repeatable rates without silently assigning real-world dimensions to a simulation tick. Later world-generation stages may also need physical units. Treating those as the same number would hide assumptions and make calibration fragile.

## Decision

Runtime mechanics may use exact reduced rational rates such as:

```text
volumeUnitsNumerator / tickDenominator
```

when the owning model is explicitly expressed in simulation units.

A future physical model must instead carry its physical dimension and convert only at an explicit boundary with the required spatial and temporal scale.

```text
physical rate
 + explicit space scale
 + explicit time scale
        ↓
conversion boundary
        ↓
runtime simulation-unit rate
```

No subsystem may infer a universal real-world tick duration or cell size from convenience.

## Why

This preserves deterministic exact runtime accounting while keeping physical calibration honest. UI/render speed remains unrelated to authoritative simulation time.

## Consequences

- Exact rational simulation rates remain valid where simulation units are the owning representation.
- Future Continuum climate/Soil/environment models may introduce physical units only with explicit conversion inputs.
- Conversion/quantization failures are handled at the boundary rather than hidden inside runtime loops.
- No implicit physical duration is assigned globally.

## Alternatives considered

Floating-point long-term rates, arbitrary fixed reference periods and one universal hidden real-world tick duration were rejected.

## Current implementation

Runtime rate types continue to support deterministic simulation-unit mechanics. The retired V12–V15 generated physical climate/Soil preparation path has been removed; future Continuum stages will define new physical-unit contracts only when real consumers require them.

## Related documentation

- [Time and Scheduling](../systems/foundations/time.md)
- [Surface Hydrology](../systems/environment/hydrology.md)
- [Soil Hydraulics](../systems/environment/soil-hydraulics.md)
- [Continuum Development Plan](../systems/world-generation/continuum-development-plan.md)
