# What the first real visualizer taught us

- Type: Entry
- Status: Historical record
- Date: 2026-08-12
- Normative: No

## Context

Early EvoForge mechanics could be proven headlessly, but developers could not simply watch Movement, ramps, caves, shafts and transition masks. The first Z-aware visualizer created that missing observation surface.

## What was observed

Several lasting lessons emerged.

### Art had to follow simulation geometry

External tilesets repeatedly failed to fit current ramp orientation/geometry or the desired calm visual language. The first developer renderer therefore used generated small procedural landscape art so visual representation could follow the simulation's actual geometry without adding asset/licensing/install dependencies.

### Z readability required physical meaning

A naïve “selected floor plus ghost floors” approach was rejected. The useful model was a horizontal cut through real 3D cell volume: solid Terrain intersecting the cut remains visible, supported surfaces stay primary, and lower content appears only through genuinely open space.

A deliberately difficult demo scene—mountain, side cave, flat-roof cavern, vertical opening, cliff and shaft—prevented the renderer from being tuned to one pretty case.

### Profiling had to distinguish computation from sampling

WASD stalls exposed repeated 3D exposure analysis/allocation. The fix was targeted primitive storage, viewport-aware caching, revision invalidation and allocation-free hot probes rather than speculative chunk infrastructure.

Far zoom later produced sampling shimmer that looked like stutter; a pixel-snapped camera made motion worse and was reverted. The lasting rule became: measure whether a problem is simulation/CPU work, frame/render work or presentation sampling before changing architecture.

### Debug readability is a real tool requirement

Transition/Shape/level-boundary overlays intentionally became visually strong because a development visualizer exists to expose structural truth, not to mimic final game UI restraint.

### Presentation also needs extensibility discipline

Concrete `RampShape` recognition had spread into renderer, art, overlay and inspector paths. This observation directly motivated typed presentation bindings instead of repeated concrete-type switches.

## Outcome

The project adopted the observer-only visualizer boundary, typed Shape presentation adapters and evidence-driven presentation optimization.

## What became canonical

Current runtime presentation now separates `SURFACE`, `INTERIOR` and `DEBUG_SLICE` perspectives; runtime interactions use production commands; generated-world preview calls production V12 generation and applies LOD only to drawing. Presentation never owns authoritative simulation/generation truth.

## Links forward

- [Visualizer and Developer Inspection Tools](../../systems/tooling/visualizer.md)
- [Geometry and Shape](../../systems/foundations/geometry.md)
- [ADR-004: Typed presentation bindings](../../decisions/004-typed-presentation-bindings.md)
- [Architecture](../../architecture.md)
