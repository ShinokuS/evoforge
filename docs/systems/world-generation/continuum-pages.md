# Continuum Technical Pages and Cache

## In plain language

A Continuum world can be enormous without keeping every coordinate in memory. A **page** is only a temporary rectangular batch of samples used for work such as inspection or later local simulation preparation.

Pages are not continents, regions, biomes or chunks of world truth. Moving a page boundary must never move a mountain, river or any other generated fact.

## Current checkpoint

Phase 0 has bounded technical page residency plus an automated scale profile around the scalar proof field:

```text
large logical Continuum domain
        ↓
ContinuumPageLayout
  global coordinate ↔ technical page key/window
        ↓
ContinuumScalarPageCache
  bounded LRU residency + metrics
        ↓
ContinuumMaterializer
        ↓
authoritative coordinate-addressed scalar field
```

Page dimensions are constructor configuration. There is deliberately no project-wide magic page size.

## Page layout

`ContinuumPageLayout` owns only technical addressing:

- configurable page width/height;
- page count for the current logical domain;
- global coordinate → `ContinuumPageKey`;
- page key → bounded `ContinuumSampleWindow`;
- clipping of only the final world-edge page.

Every page window uses global coordinates and unit sample step in this first cache proof.

## Bounded cache

`ContinuumScalarPageCache` has two explicit independent limits:

```text
maximum resident page count
maximum resident scalar-payload bytes
```

If either limit would be exceeded, least-recently-used pages are evicted before the new page becomes resident.

The byte counter is exact for stored `double` sample payloads. It does **not** claim to measure complete JVM heap overhead.

## Cache is not authority

Eviction is semantically invisible:

```text
resident page
   ↓ evict
no resident representation
   ↓ request again
authoritative field rematerializes the same global samples
```

The cache never owns generated facts. Request order or visibility can change hits/evictions, but not values.

## Metrics for diagnostics

`ContinuumPageCacheMetrics` exposes:

- hits;
- misses;
- loads;
- evictions;
- resident pages;
- resident payload bytes;
- configured page/byte budgets.

`residentKeys()` exposes current technical residency from least- to most-recently used. These observer facts are intentionally sufficient for the upcoming Continuum page/cache overlay without teaching presentation code cache internals.

## Automated scale profile

`ContinuumScaleProfileTest` runs through the existing `:simulation:scaleProfile` Gradle task and is also executed by the `Continuum Scale Profile` GitHub workflow whenever Continuum code/profile inputs change.

The current workload uses a **256×256 test page**, four resident pages, eight cold page requests and 100,000 warm cache lookups. These values define a repeatable benchmark workload only; they are not world laws or final tuning choices.

For logical square worlds of 10k, 100k and 1M cells per side the profile hard-checks:

- exactly four resident pages after the fixed workload;
- exactly the same `2 MiB` scalar payload budget;
- expected cold misses/loads/evictions;
- warm lookups cause zero additional materializations;
- local sample checksums are identical across logical world sizes;
- generous upper time bounds catch catastrophic regressions.

The report also records setup/cold/warm timing and JVM heap-used snapshots. Heap-used values are **diagnostic only** because GC/JIT make small deltas noisy; they are not presented as exact allocation counts. The hard memory guarantee at this stage is the bounded resident payload and bounded resident page count.

## Verification

Headless tests and the scale profile now prove:

- configurable page addressing and clipped edge pages;
- cache hit behavior;
- LRU eviction;
- byte-budget eviction independent of page-count budget;
- exact eviction/rematerialization equality;
- tiled and untiled requests produce the same global samples;
- 10k, 100k and 1M logical worlds keep the same resident payload budget for the same active working set;
- repeated hot-page lookup does not rematerialize;
- the scale workload is continuously runnable in CI with explicit timing and heap diagnostics.

## Visualization status

This checkpoint creates no new geographic fact, so there is no aesthetic visual acceptance yet. Page residency **is** spatially useful diagnostic information; the next Phase 0 presentation checkpoint will draw page/request/cache boundaries using these metrics and resident keys.

## Current limitations

- scalar proof pages only;
- unit sample step in cached pages;
- single-threaded cache ownership;
- no disk persistence;
- heap-used snapshots are not an allocation profiler;
- no Continuum GUI overlay yet.

These are deliberate Phase 0 boundaries, not geography semantics.

## Code and tests

```text
simulation/.../world/continuum/page/ContinuumPageKey.java
simulation/.../world/continuum/page/ContinuumPageLayout.java
simulation/.../world/continuum/page/ContinuumPageCacheMetrics.java
simulation/.../world/continuum/page/ContinuumScalarPageCache.java
simulation/.../world/continuum/ContinuumPageCacheTest.java
simulation/.../profile/ContinuumScaleWorkload.java
simulation/.../profile/ContinuumScaleProfileTest.java
.github/workflows/continuum-scale-profile.yml
```

See [Continuum Development Plan](continuum-development-plan.md), [World Generation](overview.md), and [ADR-024](../../decisions/024-continuum-large-world-architecture.md).
