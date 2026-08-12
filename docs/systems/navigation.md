# Navigation

## Purpose

Answer one structural question: **which immediate directed transitions exist from this standing position?**

## Public read contract

```java
int transitions(int x, int y, int z)
```

The result is a 26-bit mask for immediate 3D neighbors.

## Reads

Navigation reads Geometry through its generic Shape contract and resolves local departure/arrival/block contributions.

## Does not know

- concrete Shape types;
- ObjectId or actor abilities;
- dynamic occupancy/reservations;
- transition price;
- pathfinding;
- rendering.

## Locality

Transition distance is always one immediate 3D neighbor. Geometry may need to be read from nearby terrain anchors to resolve Shape roles; the current proven local read envelope is bounded and is an implementation of the current Shape model, not a license for long-range Navigation edges.

## Invariant

Temporary object occupancy must never be converted into structural Navigation topology. A structurally valid edge can be temporarily unavailable without ceasing to exist.

## Diagnostics

The visualizer F2 overlay draws the authoritative transition mask for the selected standing cell. This is presentation of Navigation truth, not a second resolver.
