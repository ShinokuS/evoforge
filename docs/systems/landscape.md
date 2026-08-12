# Landscape and Terrain

## Purpose

Own base environmental terrain independently from runtime objects and independently from Shape geometry.

## Terrain ownership

Terrain storage is conceptually:

```text
XYZ → LandscapeDefinitionId | absence
```

Absence is not a fake terrain definition.

`TerrainSystem` owns terrain presence/material identity and its storage invariants. Concrete storage is replaceable.

## Coordinated landscape mutation

Terrain and Geometry are separate authoritative owners. When one logical landscape operation must keep them coherent, `LandscapeMutations` owns that semantic operation.

Current lifecycle:

```text
placeTerrain    empty → terrain, clear stale geometry override
replaceTerrain  existing terrain definition changes, Shape override preserved
removeTerrain   terrain removed, associated geometry override removed
```

A non-default Shape therefore belongs to the lifetime of its terrain cell, not permanently to an XYZ coordinate.

## Read capabilities

`TerrainExtentLookup` exposes real occupied min/max Z without presentation semantics. `TerrainRevisionLookup` exposes a monotonic revision used by safe derived caches such as visual cutaway analysis.

## Definition data

Landscape definitions may carry mechanic-specific immutable aspects such as actor-independent traversal cost. Terrain cells still store only their definition identity.

## Does not own

Geometry, navigation, object position, rendering or camera visibility.
