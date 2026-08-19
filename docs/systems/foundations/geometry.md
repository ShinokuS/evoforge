# Geometry and Shape

## In plain language

Geometry describes **what physical space a Terrain cell occupies and how its surface connects to neighboring cells**.

A flat solid block and a ramp may use the same material but have different geometry. Water needs to know where free volume/openings exist; Navigation needs to know whether standing surfaces connect; presentation needs to draw the shape. Those consumers should all read the same neutral Shape facts instead of containing their own `if ramp ...` rules.

## Current status

The production Geometry model supports:

- default full solid Terrain cells;
- cardinal `RampShape`s;
- sparse non-default Shape overrides;
- fixed-point solid/free-volume profiles;
- face opening/sill facts used by liquids;
- top-surface boundary profiles used for surface continuity;
- structural transition ports/blocks and traversal factors;
- optional finite-world closure through `WorldGeometryLookup`.

The Shape contract is intentionally open so a future geometry can be added without changing generic consumers when the existing facts are sufficient.

## Terrain anchor and default shape

The Terrain coordinate `(x,y,z)` is the **Shape anchor**.

For present Terrain:

```text
no explicit Geometry override
        ↓
FullShape.INSTANCE
```

Only non-default shapes need sparse override state.

Under the current standing-position model, ordinary supported standing space is one cell above its supporting Terrain anchor:

```text
S = (0, 0, 1)
```

## Two independent families of Shape facts

`Shape` deliberately contains both physical and structural facts, but consumers must not conflate them.

### Physical geometry

```text
solidVolume()
freeVolumeBelow(localHeight)
boundaryOpeningFloor(CellFace)
surfaceBoundaryProfile(CellFace)
```

These answer questions such as:

- how much of the anchor cell is solid?
- how much free volume exists below a local height?
- at what height does free space connect through a face?
- what top-surface line reaches a horizontal boundary?

### Structural traversal geometry

```text
transitionPorts(relativeSource)
transitionBlocks(relativeSource)
departureTraversalFactor(relativeSource, direction)
arrivalTraversalFactor(relativeSource, direction)
```

These answer whether a supported position can depart/arrive and what Shape-owned traversal factor applies.

Water must not reinterpret Navigation ports as fluid openings. Navigation must not infer standing topology from scalar free volume.

## Local coordinates and translation independence

A Shape receives relative geometry, not the absolute world:

```text
relativeSource = sourceStandingXYZ - shapeAnchorXYZ
```

A movement direction is one immediate integer delta in `[-1,1]` per axis excluding `(0,0,0)`.

Shape logic receives no `World`, object ID, Water system, pathfinder or neighbor service. Therefore the same Shape value can be translated anywhere without hidden environmental knowledge.

## Fixed-point cell geometry

### Volume

`CellVolume` uses a normalized deterministic integer scale:

```text
EMPTY = 0
FULL  = 1_000_000
```

This is a fraction of one simulation cell's volume, not litres/cubic metres.

### Local height

`CellSpace` uses the same numeric resolution for normalized height:

```text
EMPTY_HEIGHT = 0
FULL_HEIGHT  = 1_000_000
```

Height and volume are different physical quantities even though both use the same fixed-point resolution.

## Solid and free-volume profile

A scalar solid volume alone is insufficient when Water needs to know **where** the free volume is vertically. `freeVolumeBelow(h)` therefore gives the free volume below normalized local height `h`.

### Open/empty space

For a fully empty cell:

```text
freeVolumeBelow(h) = h
```

because free cross-section is constant over height.

### FullShape

```text
freeVolumeBelow(h) = 0
```

for all local heights inside the solid cell.

### Cardinal RampShape

The current ramp is an approximately half-cell solid wedge with a linearly rising surface. The complementary free wedge below height `h` follows:

```text
freeVolumeBelow(h) = h² / 2
```

on the normalized cell scale.

`CellSpace.surfaceHeight(shape, volume)` inverts any monotonic free-volume profile deterministically using integer binary search. This is a neutral geometry operation even though Water is currently its primary consumer.

The default custom/test Shape implementation conservatively derives total free capacity from solid volume and spreads it uniformly with height; important production shapes override the exact profile.

## Physical face openings

`CellFace` names the six physical faces. It is not a movement-direction enum.

`boundaryOpeningFloor(face)` returns:

```text
lowest local height where free space connects through this face
or CellSpace.CLOSED when it does not connect
```

The default is conservative/closed: unused volume does not prove neighbor connectivity.

### Open-space semantics

Current ordinary open space is modeled as:

```text
horizontal faces -> opening floor 0
bottom face      -> opening floor 0
top face         -> full-cell height
```

### Ramp semantics

For a cardinal ramp:

```text
low horizontal face         -> 0
perpendicular side faces    -> 0
high horizontal face        -> CLOSED
bottom face                 -> CLOSED
top face                    -> full-cell height
```

This first-order sill model is enough for current liquid flow. If a future arch/tunnel shape has multiple disconnected openings or richer boundary geometry, extend a **neutral physical boundary profile** rather than putting concrete Shape checks in Water.

## Surface boundary continuity

For horizontal faces, `surfaceBoundaryProfile(face)` returns the top-surface boundary line as two fixed-point endpoint heights in canonical world-space ordering.

`SurfaceBoundaryContinuity` translates neighbor profiles by their anchor Z and compares the world-space lines:

```text
same boundary line -> continuous surface
otherwise          -> ledge/break
```

For current full blocks/ramps this correctly captures cases such as:

- parallel same-slope ramps joining laterally;
- a sloping ramp side not falsely joining a flat block at the same anchor Z;
- opposite slopes not joining;
- ramp high edge joining an upper platform;
- ramp low edge joining a lower platform.

Navigation uses this continuity fact to validate same-level cardinal standing transitions. Surface presentation uses the same fact to suppress false banks/seams.

Water still uses physical openings/free volume rather than surface continuity.

## Structural transition algebra

Each relevant supporting Shape contributes independent transition facts:

```text
departure ports
arrival ports
blocks
```

Generic resolution is conceptually:

```text
resolved = departures & arrivals & ~blocks
```

Because contributions are gathered before resolution, the result does not depend on which Shape is processed first.

### Supported-position role

For current `FullShape` and cardinal ramps:

```text
S = (0,0,1)
```

The Shape supporting the source owns departure from `S`. For immediate edge `d`, the Shape supporting the destination confirms arrival when queried from:

```text
relative source = S - d
```

A valid edge therefore requires compatible source/departure and destination/arrival roles plus no block and, where applicable, surface continuity.

This single-supported-position rule is current design, not a guarantee for every future Shape. A genuine multi-standing-position Shape should drive an explicit contract revision.

## Traversal factors

`ShapeTraversalFactor` uses normalized multiplicative-style values:

```text
NONE    = 0       shape does not own that role
NEUTRAL = 1000    owned role adds no cost multiplier
```

Current `FullShape`/`RampShape` factors are neutral. A ramp already changes the discrete transition vector/elevation and therefore path length/cost geometry; EvoForge does not add an arbitrary universal uphill penalty merely because something is a ramp.

Actor-specific preferences do not belong in universal Shape geometry.

## Navigation read locality

With current immediate transition directions and supported position `S`, arrival checks may need Shape-relative source Z values `0..2`. Combined with support/block reads, current Navigation's structural Shape read neighborhood is:

```text
X [-1, 1]
Y [-1, 1]
Z [-2, 1]
```

This is the local **read radius**, not movement distance. Structural edges remain immediate neighboring standing coordinates.

## Finite world boundary

`WorldGeometryLookup` wraps ordinary Terrain-backed Geometry:

```text
no WorldBounds configured
    -> delegate everywhere

WorldBounds configured:
    coordinate inside  -> ordinary Geometry
    coordinate outside -> FullShape
```

No fake boundary Terrain/material is created. Outside simply appears physically/structurally closed to consumers of Geometry.

## Generic-consumer law

Navigation, transition cost, liquids and generic presentation code must not branch on concrete Shape implementations merely to support a new Shape.

Simulation consumes the neutral `Shape`/`CellSpace` contracts. Presentation localizes concrete visual knowledge to typed `ShapePresentation<S>` bindings at the presentation composition root.

If a new Shape cannot be expressed by the current contract, improve the smallest neutral contract using the new real consumer as evidence.

## Invariants

- Terrain material identity and Shape geometry are separate.
- Default present Terrain resolves to full solid geometry.
- Physical opening/free-volume facts remain independent from structural movement ports.
- Shape behavior is translation-independent and local.
- Generic consumers do not use concrete Shape branching.
- Surface continuity compares world-space boundary geometry, not Shape identity.
- Out-of-bounds closure is shared through Geometry.
- Presentation interpolation/drawing never becomes authoritative geometry.

## Current limitations

The current Shape vocabulary/model does not yet cover:

- multiple supported standing positions per anchor;
- arches/tunnels with multiple complex face openings;
- continuous collision meshes;
- partially transmissive optical geometry;
- dynamic deformation physics.

These require explicit neutral contract changes when a real mechanic needs them.

## Code and tests

Primary code lives under:

```text
simulation/.../world/mechanics/geometry/
simulation/.../world/landscape/   Terrain-backed Geometry integration
```

Tests cover ports/blocks, volume profiles, face openings, boundary continuity, role/factor ownership, terrain lifecycle, finite-world closure and Navigation/Liquid integration.

## Sources

**Internal EvoForge design.** The Shape fact split and transition algebra are project-specific. The ramp formulas above are the exact current normalized geometry model, not a claim of an external physics standard.

See [ADR-002: Shape transition algebra](../../decisions/002-shape-transition-algebra.md), [ADR-004: Typed presentation bindings](../../decisions/004-typed-presentation-bindings.md), [Navigation](../traversal/navigation.md), and [Liquids](../environment/liquids.md).
