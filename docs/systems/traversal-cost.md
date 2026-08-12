# Transition Cost

## Purpose

Price an already structurally valid directed edge using the same actor-independent model consumed by Movement and future Pathfinder.

## Reads

TransitionCost combines:

- source terrain traversal data;
- source Shape departure factor;
- destination terrain traversal data;
- destination Shape arrival factor;
- geometric grid length.

Shape factors use fixed-point scale `1000 = 1.0`. Current `FullShape` and cardinal `RampShape` use neutral factors for roles they expose; there is no arbitrary ramp surcharge.

## Invariants

- TransitionCost does not decide whether an edge structurally exists; Navigation does.
- It does not inspect the moving actor.
- Future Pathfinder must consume this same model instead of implementing parallel terrain costs.
- Required missing traversal definition data is broken configuration, not a hidden default.

## Directionality

Costs are directed. Source departure and destination arrival contributions are deliberately distinct, leaving room for asymmetric future geometry without changing the consumer contract.
