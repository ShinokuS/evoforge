# Transition Algebra

EvoForge structural Navigation is built around a small bit-mask algebra. Shapes do not directly return a final set of moves. Instead they contribute facts that are composed generically.

## Transition space

One structural transition goes from a source XYZ to one of the 26 immediate three-dimensional neighbors.

```text
dx ∈ {-1,0,+1}
dy ∈ {-1,0,+1}
dz ∈ {-1,0,+1}
(dx,dy,dz) != (0,0,0)
```

This allows horizontal, vertical, and diagonal-vertical structural edges while keeping every edge local.

A movement such as a ramp ascent can therefore be one immediate edge:

```text
(0,+1,+1)
```

It is not a jump over intermediate navigation cells; source and destination differ by at most one in every coordinate.

## `TransitionMask`

`TransitionMask` maps the local `3x3x3` direction cube into bits of an `int`.

The center direction exists in the raw indexing space but is excluded from `TransitionMask.ALL`. The public Navigation result can therefore never expose `(0,0,0)` as a transition.

Important operations:

```java
TransitionMask.of(dx, dy, dz)
TransitionMask.contains(mask, dx, dy, dz)
```

The representation is primitive and allocation-free.

## Three independent facts

Every Shape can contribute three logically different masks for the current source position:

```text
departures  directions the source-supporting geometry allows leaving toward
arrivals    directions whose destination geometry accepts the transition
blocks      directions obstructed by solid geometry
```

These roles must not be collapsed into one `allowed` flag because different Shapes frequently own different halves of an edge.

## `TransitionPorts`

Departure and arrival masks are packed into one `long` using two non-overlapping bit regions.

Conceptually:

```text
long ports = [ arrivals ][ departures ]
```

Helpers include:

```text
TransitionPorts.of(departures, arrivals)
TransitionPorts.departuresOnly(mask)
TransitionPorts.arrivalsOnly(mask)
TransitionPorts.departures(ports)
TransitionPorts.arrivals(ports)
```

The packed representation keeps the hot composition path primitive while retaining the semantic distinction between roles.

## Composition

For one Navigation source, contributions from every relevant Shape are OR-accumulated:

```text
departures = dep(A) | dep(B) | dep(C) | ...
arrivals   = arr(A) | arr(B) | arr(C) | ...
blocks     = blk(A) | blk(B) | blk(C) | ...
```

Resolution is then:

```text
resolved = departures & arrivals & ~blocks
```

and finally restricted to valid neighbor directions:

```text
resolved &= TransitionMask.ALL
```

The result is independent of Shape processing order because all contributions are accumulated with OR before the final boolean expression.

## Example: flat Full-to-Full edge

Suppose an object stands above Full A and wants to move east onto Full B.

```text
source A standing position       destination B standing position
          ●  ───────────────→               ●
          █                                  █
       Full A                             Full B
```

The source Full contributes an east departure. The destination Full contributes an east arrival when evaluated from the source relative to its anchor.

```text
departures contains east
arrivals   contains east
blocks     does not contain east
```

Therefore east survives composition.

If Full B is absent, the arrival bit disappears and the edge disappears. Navigation does not need a special “destination exists” test.

## Example: lower Full to Ramp

For a `POSITIVE_Y` ramp:

```text
A -> B = (0,+1,+1)
```

The lower Full contributes the diagonal-up departure. The Ramp contributes the corresponding arrival.

```text
Full A: departure (0,+1,+1)
Ramp B: arrival   (0,+1,+1)
```

Both are required.

For the reverse edge:

```text
B -> A = (0,-1,-1)
```

the Ramp contributes departure and the lower Full contributes arrival. This is why the resolver must be able to read the Full anchor below the destination standing position.

## Example: blocking solid volume

Permission alone does not allow walking through terrain.

A Shape whose occupied body lies in the path contributes the corresponding block bit. Even when another geometry pair contributes matching departure and arrival bits:

```text
1 & 1 & ~1 = 0
```

The blocked direction is removed.

`SolidCellBlocking` centralizes the common solid-cell volume rules shared by `FullShape` and `RampShape`.

## Directed edges

The algebra resolves one source independently. Nothing automatically mirrors an edge.

```text
A -> B
```

does not imply:

```text
B -> A
```

Bidirectional topology exists only when the reverse query independently receives the required departure, arrival, and no blocking contribution.

This makes one-way future geometry possible without changing Navigation.

## Why this algebra matters

The algebra replaces type-specific pair logic such as:

```java
if (sourceShape instanceof FullShape
        && destinationShape instanceof RampShape) {
    ...
}
```

with local declarations from independent Shapes. Adding new compatible geometry can therefore compose with existing geometry automatically.

The strongest tests validate the algebra itself: order independence, center-bit sanitization, missing endpoint removal, block precedence, and comparison with an independent reference resolver.
