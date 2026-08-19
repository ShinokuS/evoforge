# ADR-004: Typed presentation bindings for concrete domain variants

- Status: Accepted
- Scope: Presentation boundary
- Decision: Generic presentation consumes domain abstractions plus typed presentation registries; concrete simulation variants are represented by specialized adapters registered at the presentation composition root.

## Context

The first procedural visualizer independently recognized `RampShape` in several render/HUD/overlay paths. Repeating concrete-type checks would make every new Shape require changes across unrelated presentation consumers.

Presentation may legitimately know how a concrete domain variant looks, but that knowledge must not leak into pure simulation contracts or spread through generic renderers.

## Decision

Concrete visual knowledge is localized in typed adapters such as `ShapePresentation<S>`. Exact simulation types are registered with a presentation registry at the composition root. Generic rendering/inspection code receives the abstract domain value plus its presentation adapter and does not branch on concrete implementation classes.

Simulation-side Shapes do not contain texture, sprite, debug-label or libGDX methods.

## Why

The boundary preserves a headless reusable simulation while keeping the UI extensible without central `instanceof` chains.

## Consequences

- A new compatible Shape adds one presentation binding/registration rather than edits to every renderer.
- Presentation-specific data remains outside `simulation`.
- The same pattern can be used where other open domain variants need concrete visual representation.
- Exact-type registration is deliberate; hidden fallback dispatch through inheritance is avoided.

## Alternatives considered

Embedding presentation methods inside `Shape` was rejected because it would couple pure simulation to libGDX/pixels. Repeated `instanceof`/class switches in generic presentation were rejected because they become the extension mechanism.

## Current implementation

Runtime visualizer Shape drawing, labels and overlays use typed presentation bindings/registries. World-generation preview similarly renders generated facts through presentation-specific helpers while calling production generation algorithms rather than putting visual concerns into generated fact interfaces.

## Related documentation

- [Visualizer and Developer Inspection Tools](../systems/tooling/visualizer.md)
- [Geometry and Shape](../systems/foundations/geometry.md)
- [Architecture](../architecture.md)
