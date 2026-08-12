# 2026-08-12 — What the first real visualizer taught us

**Status:** Historical development note

For several architecture slices EvoForge could prove mechanics headlessly but could not simply *watch the world*. The first Z visualizer changed that: Movement, ramps, caves, shafts and transition masks became directly observable.

## Art direction changed because the constraint changed

We initially searched for external tilesets. Several packs either did not fit ramp geometry, orientation or the desired calm visual language. The useful lesson was not “find a better pack”; it was that the current development renderer needed a visual representation shaped by simulation geometry.

The project therefore adopted generated 16×16 procedural landscape art. This removed licensing/install friction and let ramp lighting stay fixed in world coordinates while geometry changed orientation.

## Z readability required real geometry

A simple “show selected floor and ghost nearby floors” model was rejected. The useful mental model became a horizontal cut through actual 3D cell volume:

- solid terrain intersecting the cut remains body;
- the supported selected surface stays primary;
- lower terrain appears only through genuinely open space;
- cover and exterior exposure affect how underground/open spaces read.

The deliberately difficult demo scene — mountain, side cave, flat-roof cavern, vertical opening, high cliff and deep shaft — was valuable precisely because it prevented the renderer from being tuned to one pretty case.

## Performance problems appeared early and were worth fixing early

WASD initially caused visible stalls. Profiling showed repeated 3D exposure BFS allocation/rebuild and hot sparse reads allocating coordinate keys. The solution was not broad chunk infrastructure: primitive BFS storage, viewport-aware padded analysis caching, authoritative revision invalidation and allocation-free hot probes addressed the measured costs.

Far zoom later exposed a different problem: sampling shimmer looked like frame stutter. Pixel-snapping the render camera made movement more stepped and was reverted. Continuous camera movement plus far-zoom linear sampling worked better.

The lasting process rule is: instrument first, distinguish CPU/frame-time problems from presentation sampling problems, then fix the demonstrated path.

## Readability is development tooling

F2/F3 diagnostics and the active-Z perimeter became deliberately bold, screen-pixel-sized overlays. A debug visualizer is not the final game UI; its job is to make structural truth obvious enough that future Pathfinder/agent work can be inspected quickly.

## Architectural lesson after acceptance

The finished visualizer also exposed a code smell: `RampShape` recognition had spread into renderer, procedural art, F3 and inspector code. That led directly to the typed Shape presentation binding decision. The important lesson is that even non-authoritative presentation needs the same extension discipline as simulation if it is expected to live for years.
