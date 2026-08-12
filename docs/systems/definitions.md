# Definitions

## Purpose

Represent immutable content/configuration separately from mutable runtime state.

## Owns

Definition loading, stable source keys, typed runtime ids and mechanic-specific compilation of definition aspects.

## Current semantics

Source definitions use stable names such as `namespace:name`. Runtime systems consume typed numeric ids; numeric runtime ids are not persistence identity.

Definitions are composition-driven. A mechanic compiles only the aspect it owns into its own immutable lookup. Current examples include object movement rate and landscape traversal cost.

```text
source JSON
   ↓
definition loader
   ↓
typed definition identity
   ↓
mechanic-specific compiler/store
```

Missing data that is required by an otherwise configured mechanic is a configuration/programming failure rather than a hidden gameplay fallback.

## Does not own

Object existence, position, movement actions, terrain cells or any other mutable runtime property.

## Extension rule

Adding content that uses existing mechanics should normally be data-only. Adding a new mechanic-specific aspect should add its own compiler/store rather than fields to a universal definition object.

## Deferred

Persistence-facing definition migrations and mod/plugin packaging are not fixed yet.
