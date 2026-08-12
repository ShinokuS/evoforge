# Decision 001 — Authoritative ownership and narrow capabilities

**Status:** Accepted

## Problem

A large simulation becomes difficult to reason about when multiple systems can mutate the same fact or when consumers receive a broad `World` object and reach through it for whatever they need.

## Decision

Every mutable authoritative property has one owner. Cross-system consumers receive the narrowest read or mutation capability required for their responsibility. Composition roots may hold owners to wire the application; runtime observers do not.

## Consequences

- ownership questions have concrete answers;
- storage can change behind semantic capabilities;
- headless tests can construct narrow fixtures;
- presentation cannot accidentally become simulation authority;
- adding a capability is explicit API surface rather than hidden service lookup.

## Rejected direction

A universal mutable world/cell/entity object was rejected because it makes ownership implicit and causes unrelated mechanics to accumulate in central structures.
