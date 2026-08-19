# ADR-010: World Atlas owns durable generated facts

- Status: Accepted
- Scope: World-generation fact ownership
- Decision: Durable pre-runtime generation results are immutable typed Atlas facts with representation hidden behind semantic field contracts; precise macro facts remain separate from runtime/materialized projections.

## Context

Generation needs durable facts such as elevation, geology, climate normals and drainage before detailed Terrain/Water/runtime state exists. Writing those facts directly into runtime owners would collapse generation and simulation. Treating them as disposable intermediates would make causal dependencies/provenance unclear.

Elevation exposed the first critical representation issue: integer Terrain Z is suitable for materialization but can destroy real sub-cell gradients needed by later macro algorithms.

## Decision

`WorldAtlas` is the immutable composition boundary for durable generated world facts. Each layer exposes a typed semantic read interface while hiding dense/sparse/internal representation.

Elevation retains precise fixed-point subunits (`1 cell = 1_000_000 subunits`) plus a discrete floor-derived surface-cell view. Macro consumers use precise elevation when their model depends on gradients; materialization may use the discrete view.

Generation remains causal and staged. `WorldAtlasGenerator` orchestrates typed algorithms and dependencies but does not absorb domain algorithms or runtime materialization.

## Why

This preserves causal pre-runtime facts, avoids making materialization resolution accidental macro physics, and keeps future representation changes behind stable contracts.

## Consequences

- Later generated layers can depend on earlier facts explicitly.
- Storage may change without redefining public generated semantics.
- Generation revisions protect intentional changes to durable facts.
- Atlas remains provenance/preparation truth rather than live runtime Terrain/Water ownership.
- Biome/content labels are not allowed to become unexplained primary causes of elevation/geology/hydrology.

## Alternatives considered

Generating runtime Terrain blocks directly from the seed was rejected because later causal stages need durable macro facts. Integer-only elevation was rejected because quantization creates false flats/sinks. Exposing current dense arrays was rejected as representation leakage. Biome-first generation was rejected for this foundation.

## Current implementation

The Atlas now contains seven typed facts: Genesis, Elevation, Geology, Climate Normals, Drainage, Hydrography and Surface Hydrology. Historical generation revisions span V1–V12; V12 is the accepted base-terrain generation baseline. Geology/hydrography/initial-Water algorithms behind some current fields are explicitly provisional, but the typed fact ownership boundary remains canonical.

## Related documentation

- [World Atlas](../systems/world-generation/world-atlas.md)
- [World Genesis](../systems/world-generation/world-genesis.md)
- [World Generation](../systems/world-generation/overview.md)
- [World Materialization](../systems/world-generation/world-materialization.md)
