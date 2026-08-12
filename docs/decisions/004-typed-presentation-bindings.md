# Decision 004 — Typed presentation bindings for concrete domain variants

**Status:** Accepted

## Context

The first procedural visualizer initially recognized `RampShape` independently in landscape rendering, F3 diagnostics, inspector labels and procedural art selection. Although presentation may legitimately represent concrete domain semantics, repeating concrete-type checks creates an extension path where every new Shape adds branches across unrelated consumers.

## Decision

Generic presentation does not branch on concrete Shape classes. Exact Java types are registered with specialized `ShapePresentation<S>` bindings in a presentation composition root.

A binding may know its concrete simulation type; generic renderer/HUD/overlay consumers know only `Shape` plus `ShapePresentationRegistry`.

Registration is type-safe and exact-type based. String visual IDs and simulation-side rendering methods are rejected.

## Why not put presentation on Shape?

Pixels, textures, debug labels and libGDX dependencies do not belong in the pure simulation geometry contract. The adapter boundary keeps simulation reusable/headless.

## Why now?

The problematic extension mechanism already existed in several real consumers. Waiting for another Shape would allow the branching pattern to become established architecture; no speculative future semantics are required to replace the dispatch mechanism itself.

## Consequences

A future Shape that fits existing presentation needs adds its binding and registration. Existing generic renderers do not change merely to recognize the new concrete class.
