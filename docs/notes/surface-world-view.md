# Surface World View

## Goal

The normal EvoForge world view is a coherent top-down projection of the open surface. A global Z slice is a development perspective, not the primary way to read the world.

Presentation must reveal only objective simulation facts and must not create a second navigation, hydrology or movement model.

## View model

`VisualizerState` owns three presentation perspectives:

- `SURFACE` — default open-world top-down view;
- `INTERIOR` — an explicitly entered covered local space;
- `DEBUG_SLICE` — whole-world Z inspection, toggled with `F7`.

Changing perspective never changes simulation coordinates or topology. Selection and an unfinished Move target draft survive Surface/Interior transitions.

Diagnostic overlays are independent from perspective. The F1 panel exposes Grid, Height contours, Move route, Transitions, Shape directions, Occupancy, Vision and Technical inspector.

## Surface projection

`SurfaceProjectionResolver` reads the highest authoritative terrain anchor for each visible XY column from `TerrainSurfaceLookup` and derives its standing Z. It also exposes the current highest Water Z from `WaterSurfaceLookup`.

There is no flattened presentation world or cached copy of the map. `SurfaceLandscapeRenderer` visits only camera-visible XY cells.

Objects render on Surface only when their authoritative transform matches the visible standing position of the highest terrain surface in that column. An object inside a covered cave therefore remains physically present but is not drawn through its roof.

### Relief

Ordinary top-down relief is encoded by cheap cardinal height comparisons and terrain-edge treatment. The optional `Height contours` overlay remains a separate two-tone diagnostic: a light line marks the high side and a dark line marks the low side.

The default Surface view therefore remains readable without forcing debug contours on every frame.

## Interior and portals

A `ViewPortal` is presentation metadata connecting one visible surface anchor to an `InteriorView` and one interior anchor.

A portal changes camera/view context only. It never teleports an object, creates a navigation edge or bypasses `MoveTo`.

The current fixed portal registry exists for visualizer scenarios. A future Structure domain may provide the same read-only presentation contract when real structure identity exists.

An Interior includes its real physical boundary cells. Walls and the real doorway remain ordinary Geometry/Navigation facts.

## Cell-centric interaction

LMB belongs to the world cell, not to a glyph hitbox.

Every ordinary LMB first updates `selectedCell`. The cell can then contribute zero or more contextual capabilities:

- an object contributes object actions such as `Move` / `Cancel move`;
- a portal contributes `View inside` / `Return outside`;
- if both are present, one context menu aggregates the applicable actions.

Portal glyphs are therefore visual hints only. They do not own an independent click target.

Selection and Move preview use thin corner brackets rather than full-cell fills, so terrain, Water and objects stay readable underneath.

## Move preview and cancellation

An unfinished Move command is separate from inspection state. The selected mover remains selected while the user inspects candidate cells or switches Surface/Interior.

While targeting, the hovered destination uses the same mover-aware `MoveTo` planning policy as authoritative movement:

- green — reachable;
- red — blocked/no route;
- amber — bounded disposable search still running.

The preview search does not reserve occupancy or create authoritative movement. Clicking submits the normal command path; Movement remains final truth.

There are two cancellation levels:

1. `Esc`/RMB during targeting cancels only the unfinished UI target draft;
2. `Cancel move` submits `CancelMoveToCommand` for an already active route.

An already scheduled atomic Movement edge is allowed to finish. Cancellation prevents the following edge from starting, then releases the MoveTo claim with `movement:move_to_cancelled`.

`Esc` hierarchy is:

1. close context menu;
2. cancel unfinished Move target;
3. leave Interior;
4. leave Debug Slice;
5. return to the scenario browser.

## Surface Water

### Optical depth

Surface Water opacity is derived from authoritative XYZ Water and Geometry, not just the amount in the highest wet cell.

`WaterOpticalDepthResolver` starts at `WaterSurfaceLookup.topZ(x,y)` and accumulates contiguous Water height downward using `CellSpace.surfaceHeight(shape, amount)`.

Work is capped at three full cell heights. A 256-entry precomputed opacity LUT maps normalized depth to alpha. Deep Water becomes visually opaque without making render cost proportional to arbitrary lake depth.

Current presentation bounds remain:

```text
alphaMin = 0.18
alphaMax = 0.96
optical depth cap = 3 full cells
```

Covered Water is not visible through a higher solid terrain surface.

### Actual-flow motion

Water animation is driven by the objective latest solver transfer exposed through `WaterFlowLookup`.

`WaterMotionResolver` does **not** infer motion from a possible hydraulic slope. If the solver did not actually transfer Water through a cell in the latest flow step, presentation classifies the cell as `CALM`.

Actual dominant transfer maps to:

- `WEST`, `EAST`, `SOUTH`, `NORTH` for horizontal flow;
- `FALLING` for negative-Z transfer;
- `CALM` when there is no current transfer sample.

This distinction matters for dormant/equalized pools: a geometrically possible slope is not enough to make a settled lake look like it is still flowing.

Calm Water uses one shared slow global animation phase, so neighbouring lake cells shimmer together without travelling per-cell direction. Active horizontal flow uses faster directional travelling detail. Falling Water has a distinct churn treatment.

There is no persistent visual velocity field, per-cell animation object, flow history or particle emitter.

## Rain presentation

Rain is a fixed-budget screen-space effect driven by weather presentation intensity.

The current renderer uses at most 160 deterministic streaks, split into two visual depth bands. Intensity changes the number/strength of visible streaks but never creates world objects and never scales work with world size.

A restrained screen-space rain veil plus longer, higher-contrast near streaks makes active rain immediately legible at normal desktop zoom while preserving terrain inspection.

There are no per-frame rain allocations or unbounded particle populations.

## Hydrology-aware inspector

The selected-cell card reports the same facts the user can actually see.

In `SURFACE` mode it resolves:

- selected standing XYZ;
- highest visible terrain definition and its actual terrain Z;
- visible Water amount and its actual Water Z;
- bounded optical Water depth;
- SoilMoisture at the supporting terrain cell;
- effective local Soil hydrology capacity;
- object count at the selected standing cell.

`Technical inspector` additionally exposes local infiltration limit, surface Water storage capacity, Shape, Occupancy, Navigation transition count and object id where applicable.

This avoids the old misleading pattern where the renderer showed surface Water while the card queried only the selected standing Z and reported `Water = 0`.

## UI presentation

Visualizer diagnostic UI is content-sized. `GlyphLayout` measures current strings; panels add small padding and clamp only to the viewport.

The right side follows one layout flow:

```text
selected cell/object inspector
            ↓
F1 debug panel
```

When no selection exists, the inspector reserves no space and the debug panel returns to the top margin. The two panels do not independently choose overlapping positions.

### Runtime FreeType font

The former bitmap-font experiments scaled or pre-rasterized small atlases and made text quality dependent on generated PNG assets. That path has been removed from the current visualizer UI.

`VisualizerUiAssets` now owns one bundled TTF and generates two fonts once at visualizer startup through libGDX FreeType:

- body: 22 px;
- title: 25 px.

The generated fonts are used at their requested pixel size with integer positioning. There is no font-image asset to keep synchronized, no sharpening shader and no runtime scaling of a smaller bitmap. Changing UI text size later means generating a different source pixel size rather than stretching an existing texture.

The scenario-browser `uiskin` remains separate because it is an existing working UI surface and is not part of this runtime-font replacement.

## Performance rules

The Surface/Interior visualizer follows these bounds:

- surface terrain work is camera-visible XY only;
- no flattened presentation world is stored;
- relief uses local cardinal height comparisons;
- Water optical-depth work stops after three full cells;
- Water motion reads sparse latest actual-flow facts and stores no presentation velocity state;
- rain has a fixed screen-space budget independent of map size;
- Move preview exists only while targeting and advances with a bounded search budget;
- context menus/debug controls are immediate-mode rather than persistent per-cell widgets;
- UI geometry is measured from current content;
- UI fonts are generated once at startup at their final requested pixel size;
- Interior rendering is clipped to `InteriorView` bounds;
- whole-world Z slicing reuses the existing cutaway renderer and remains debug-only.

If profiling later identifies a real hotspot, indexing/caching belongs at the owner of the relevant objective fact rather than in a copied presentation simulation.

## Acceptance scenarios

The Water/Hydrology visual pass should be evaluated primarily through:

- `Rain Cycle` — dry start, local Soil-capacity variation, puddle onset, shielded roof, finite lake and evaporation;
- `Water Z Flow` — real stacked Water, vertical fall, active-flow animation and eventual dormant calm state;
- `Water Geometry Stress` — finite spreading, barrier detour and Ramp face rules;
- `Surface / Interior Move` — selection persistence, physical doorway navigation, context interaction and safe Move cancellation.

The PR remains Draft until automated checks and desktop visual/performance acceptance are both complete.
