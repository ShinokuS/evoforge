# Geometry and Shape

## Purpose

Describe local structural geometry over existing terrain without coupling generic consumers to concrete Shape classes.

## Ownership

Geometry is separate from terrain material identity. Presence of terrain with no override resolves to `FullShape.INSTANCE`; sparse geometry state stores only non-default overrides.

## Shape contract

`Shape` is an open declarative local contract. It contributes:

```text
transition departures
transition arrivals
transition blocks
intrinsic departure traversal factor
intrinsic arrival traversal factor
```

A Shape receives only coordinates relative to its own terrain anchor and the queried local direction. It does not query the world, neighboring Shapes, Navigation or ObjectIds.

## Transition algebra

For a candidate local edge, contributions are combined generically:

```text
resolved = departures & arrivals & ~blocks
```

Contributions are accumulated before resolution, so processing order and concrete Shape type do not determine topology.

Current edges are limited to the 26 immediate 3D neighbors (`dx/dy/dz ∈ [-1,1]`, excluding zero vector).

## Current supported-position model

Current production `FullShape` and cardinal `RampShape` use one supported standing position at:

```text
terrain anchor + (0, 0, 1)
```

This is a current working model, not a promise that every future Shape has one supported position. A real Shape requiring a richer model must drive a coordinated revision of Shape, Navigation read envelope and TransitionCost support lookup.

## RampShape

`RampShape` has four cardinal singleton orientations. `riseX()` / `riseY()` expose the objective cardinal direction in which the ramp rises; this is geometry data used by both its topology and specialized presentation binding.

## Generic-consumer rule

Navigation, TransitionCost and generic presentation renderers do not branch on `RampShape`, `FullShape` or future concrete Shape classes. Concrete knowledge is localized to implementation/binding code and composition.

See [Shape Transition Algebra decision](../decisions/002-shape-transition-algebra.md) and [Adding a Shape](../guides/adding-a-shape.md).
