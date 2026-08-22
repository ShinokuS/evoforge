# Stage 4 — Map / Zoom Performance Proof

## In plain language

The logical world may be enormous, but the map works only with what is useful for the current screen and its immediate likely next view.

When the user moves or zooms:

1. the viewport chooses the map resolution;
2. visible tiles always get first priority;
3. nearby tiles and the likely next zoom level are prepared in advance within a hard budget;
4. already cached overlapping tiles are reused;
5. work for an area the camera has already left is discarded before it wastes CPU time;
6. if requested detail is still not ready, a ready coarser parent temporarily covers it instead of a blank hole;
7. CPU, GPU and job queues remain bounded.

The camera never changes world truth. Map tiles are disposable read-only presentation artifacts.

## Built in Stage 4

- multi-resolution map tile keys with source revision;
- replaceable tile-generator contract;
- direct tile generation from the same coordinate-addressed Continuum field;
- bounded asynchronous CPU tile service;
- single-flight identical requests;
- separate visible and speculative queues, with visible work always first;
- cancellation of obsolete queued work after pan/zoom;
- bounded parallel tile workers;
- LOD hysteresis so tiny wheel changes do not repeatedly switch levels;
- camera-intent-aware prefetch for pan, zoom-in and zoom-out;
- prewarming of the next finer/coarser level;
- bounded ready-tile LRU cache;
- always-available coarse root fallback for the current revision;
- stale-revision rejection;
- bounded texture cache;
- faster tile generation with no redundant internal buffer copy;
- faster render-thread texture conversion using a precomputed palette and direct pixel-buffer writes;
- `F2` map viewer with drag-to-pan and wheel zoom.

## Important boundaries

Stage 4 deliberately uses a smooth synthetic scalar field. It is not terrain or geography.

Stage 4 does **not** create continents, oceans, elevation, tectonics, rivers, lakes, climate or simulation LOD. Those belong to later canonical stages.

## Automated checks

Tests prove:

- simultaneous requests for one missing tile share one generation job;
- visible work overtakes queued prefetch work;
- obsolete queued work is removed when camera demand moves;
- a coarse parent is always available while detail is pending;
- a settled viewport has the next normal zoom level already prepared;
- ready storage and outstanding work remain bounded;
- stale source revisions cannot be displayed as current;
- zoom keeps the world point under the cursor stable;
- a small pan reuses the existing working set;
- camera movement never changes world truth/revision;
- render resources are bounded and evicted correctly.

## Performance proof

The scale profile uses a 1600×900 viewport. Even when logical world size grows from one million to one billion units per side, map work remains tied to the viewport rather than total world area.

After the responsiveness optimization, representative CPU tile payload on GitHub runners remains only a few MiB (roughly 2.3–2.8 MiB in the large-world scale runs). The larger bounded lookahead intentionally spends a small amount of extra memory to reduce visible fallback.

A separate real-thread responsiveness profile uses four background workers and the same synthetic field as the visualizer. One representative GitHub runner measured:

- cold map to fully detailed: **~171 ms**;
- normal next LOD after the view was prewarmed: **0 fallback tiles on the first frame**;
- substantial pan: **0 fallback tiles on the first frame** in that run;
- background preparation after the pan: **~88 ms**;
- retained CPU map payload: **~3.2 MiB**.

Exact milliseconds vary by hardware, so the architecture does not rely on those exact numbers. CI enforces a generous one-second upper safety gate while recording actual latency. The important behavioral invariant is that normal settled navigation should usually arrive at already-prepared data, and any fallback remains a short-lived safety mechanism rather than the normal visual state.

## F2 manual check

Run the desktop visualizer and press `F2`.

```text
Left mouse drag  move
Mouse wheel      zoom around cursor
Home             whole world
G                tile diagnostics
Esc              back
```

By default the screen is mainly the map. Technical tile borders are hidden.

With `G` enabled:

- green = requested detail is ready;
- orange = a coarser parent is temporarily covering detail still being prepared.

After this optimization, orange should be much rarer and much shorter-lived during normal pan/zoom. There must still never be blank holes.

## Done when

Stage 4 is complete only after Gradle, Docs and both scale/responsiveness profiles are green and the user manually accepts the map behavior. After acceptance, stop before Stage 5 unless explicitly asked to continue.
