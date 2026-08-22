# Stage 4 — Map / Zoom Performance Proof

## In plain language

The logical world may be enormous, but the map should work only with what is needed for the current screen.

When the user moves or zooms:

1. the viewport decides which map resolution is appropriate;
2. only visible tiles plus one small prefetch ring are requested;
3. already cached overlapping tiles are reused;
4. identical concurrent requests share one generation job;
5. if requested detail is not ready yet, a ready coarser parent is shown temporarily;
6. CPU and render caches stay bounded.

The camera never changes world truth. Map tiles are disposable read-only presentation artifacts.

## Built in Stage 4

- multi-resolution `ContinuumMapTileKey` with source revision;
- replaceable tile-generator contract;
- direct scalar-map tile generation from the same coordinate-addressed Continuum field;
- bounded asynchronous CPU tile service;
- single-flight identical requests;
- bounded pending/running job backlog during rapid camera movement;
- LRU eviction of ready CPU tiles;
- always-available coarse root fallback for the current revision;
- viewport-driven automatic LOD selection;
- one-tile prefetch ring;
- stale-revision rejection;
- bounded presentation resource cache used for textures;
- `F2` Stage 4 map viewer with drag-to-pan and wheel zoom.

## Important boundaries

Stage 4 deliberately uses a smooth synthetic scalar field. It is not terrain or geography.

Stage 4 does **not** create:

- continents or oceans;
- elevation/relief;
- tectonics;
- rivers or lakes;
- climate;
- simulation LOD.

Those begin in later canonical stages.

## Automated checks

Tests prove:

- two simultaneous requests for the same missing tile create one generation job;
- a coarse parent is available while requested detail is still pending;
- ready CPU tile storage is bounded;
- outstanding async work is bounded during rapid camera churn;
- old source revisions cannot be reused as current map tiles;
- zoom keeps the logical world point under the cursor stable;
- zooming in selects a finer map level;
- a small pan reuses most of the existing tile working set;
- camera movement never changes the source revision;
- the presentation resource cache evicts least-recently-used resources and disposes them.

## Scale profile

The automated profile uses a 1600×900 viewport and the same stress movement pattern on increasingly large logical worlds.

On the first Stage 4 GitHub runner pass:

| Logical world side | Visible tiles | CPU tiles after stress | CPU tile payload |
|---:|---:|---:|---:|
| 1,000,000 | 16 | 81 | 1,327,104 bytes |
| 100,000,000 | 36 | 37 | 606,208 bytes |
| 1,000,000,000 | 16 | 81 | 1,327,104 bytes |

The important result is not the exact timing. Increasing logical world area does not make the viewport allocate data proportional to the whole world.

## F2 manual check

Run the desktop visualizer and press `F2`.

Controls:

```text
Left mouse drag  move around the logical world
Mouse wheel      zoom in/out around the cursor
Home             fit the whole world
G                show/hide tile diagnostics
Esc              return to menu
```

By default the screen is mainly the map. Technical tile borders are hidden.

With `G` enabled:

- green border = requested detail is already ready;
- orange border = a coarser ready parent is temporarily filling that area;
- CPU/GPU cache and async-job counts are shown.

There should be no blank holes while finer detail is loading.

## Done when

Stage 4 is complete only after:

- Gradle tests are green;
- Docs Site is green;
- Continuum Scale Profile is green;
- pan/zoom is understandable and usable in `F2`;
- no unbounded cache/job growth is visible;
- the user manually accepts the Stage 4 map.

After acceptance, stop before Stage 5 unless explicitly asked to continue.
