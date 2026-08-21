# Landscape and Terrain

## In plain language

Landscape is the authoritative owner of the world's **solid Terrain cells and their material identity**.

A coordinate can contain Terrain such as topsoil, sand or granite, or no Terrain at all. Terrain material answers “what solid material is here?”; Geometry answers “what shape does that solid occupy?”; liquids answer “what finite fluid is here?”. These facts remain separate even when they share one XYZ coordinate.

There is no fake `air` material for ordinary empty space.

## Current status

Terrain state is conceptually:

```text
XYZ -> LandscapeDefinitionId | absence
```

`TerrainSystem` owns presence/material identity. `LandscapeMutations` coordinates semantic Terrain mutations that also have Geometry lifecycle consequences.

Current storage is sparse, but storage layout is an implementation choice rather than public semantics.

## Terrain ownership

### Owns

- whether solid Terrain exists at an XYZ;
- which `LandscapeDefinitionId` it uses;
- Terrain-specific mutation invariants;
- derived Terrain extents/revision facts maintained from accepted mutations.

### Does not own

- object identity/position;
- Shape override state;
- Navigation/path routes;
- free liquid;
- retained Soil-liquid composition;
- world-generation page/cache state.

## Empty space is absence

Ordinary open space is represented by no Terrain, not by `core:air`.

A future streaming model must distinguish real empty space from unloaded/unknown state explicitly; those concepts must never be conflated.

## Coordinated Landscape mutation

Terrain and Geometry are different owners, but changing a Terrain anchor has Shape consequences. `LandscapeMutations` coordinates those owners.

```text
external command ─┐
future generation ├─> LandscapeMutations
future erosion ───┘         │
                       ┌──────┴──────┐
                       ↓             ↓
                  Terrain owner   Geometry owner
```

- placing Terrain clears any stale Shape override;
- replacing Terrain material preserves the current Shape override;
- removing Terrain also removes its associated Shape override.

## Material identity, Geometry and mechanic properties

One Terrain cell may participate in several independent facts:

```text
LandscapeDefinitionId   material/content identity
Shape                   local solid/free geometry
SurfaceTraversalCost    intrinsic traversal contribution
SoilProperties          porous capacity/permeability
surface retention       local free-liquid retention behavior
```

They are not collapsed into one mutable Terrain record.

## Hydrology relationship

Hydrology reads Terrain-owned properties but mutable liquid state belongs elsewhere:

```text
Terrain identity/properties
        ↓
Soil/surface-retention lookups

SoilLiquidSystem
        retained constituents

LiquidSystem
        free-liquid identity + quantity
```

Wetness is derived from separate Water/Soil owners rather than stored as a Terrain flag.

## Continuum relationship

The old Atlas/prepared-field materialization path has been retired. The current Continuum foundation does not yet write generated Terrain into runtime Landscape.

Future XYZ materialization must cross an explicit boundary:

```text
Continuum generated facts
        ↓
bounded materialization/transfer
        ↓
LandscapeMutations + Geometry/Soil owners
        ↓
ordinary mutable runtime truth
```

Page/cache boundaries are technical representation only. A Terrain fact at one authoritative XYZ must not change because a different page is loaded, evicted or rendered.

## Optional finite world bounds

Finite containment is configured by `SimulationAssembly`, not by placing fake wall Terrain around the map. Outside configured bounds, shared Geometry provides closed containment without inserting fake Terrain cells.

## Landscape is not a WorldObject

Terrain cells do not receive one runtime `WorldObject` identity each. Objects and Terrain share coordinates but have different lifecycle/identity semantics.

## Invariants

- `TerrainSystem` is the only mutable owner of Terrain presence/material identity.
- Ordinary open space is absence, not a fake material definition.
- Cross-owner Terrain/Shape lifecycle uses `LandscapeMutations`.
- Material identity and Shape geometry remain independent facts.
- Mutable free/retained liquid never becomes a Terrain field.
- Finite bounds use shared Geometry closure rather than fake boundary Terrain.
- Continuum page/cache state must never become Landscape truth.

## Current limitations

Not yet defined:

- Continuum page dimensions/cache policy;
- loaded/unloaded/unknown runtime state;
- streaming generation boundaries;
- persistence/save representation;
- runtime erosion/material transformation;
- cave/open-volume materialization beyond later Continuum stages.

## Code and tests

Primary runtime code lives under:

```text
simulation/.../world/landscape/
```

Tests protect place/replace/remove semantics, definition identity, Geometry lifecycle, traversal invalidation, finite-world containment and separation between material capabilities and liquid state.

## Sources

**Internal EvoForge design.** Landscape ownership and cross-owner mutation coordination are project architecture.

See [Definitions](../foundations/definitions.md), [Geometry](../foundations/geometry.md), [Navigation](../traversal/navigation.md), [Liquids](liquids.md), [Soil Hydraulics](soil-hydraulics.md), [World Generation](../world-generation/overview.md), and [ADR-024](../../decisions/024-continuum-large-world-architecture.md).
