# Movement

## Purpose

Execute one concrete adjacent actor movement as deterministic timed simulation work.

## Inputs

A `MoveStepCommand` expresses external intent to start one adjacent movement. Movement reads:

- actor Spatial position;
- structural Navigation for the requested edge;
- shared TransitionCost;
- definition-backed `MovementRate`;
- current Movement action state.

## Timing

Movement duration is derived from transition cost and actor movement rate. Integer carry/remainder is retained per actor so repeated steps do not accumulate systematic `ceil` bias.

Starting movement does **not** teleport the actor. A `MovementAction` records in-flight domain state and schedules completion through ProcessScheduler.

## Authoritative position

During an action, Spatial remains at the source. On completion Movement revalidates the world state; only a valid completion performs `SpatialSystem.move`.

This prevents a second authoritative/interpolated position from existing inside simulation. Future visual interpolation, if added, remains presentation-only.

## Revalidation

Completion-time revalidation protects against terrain/geometry changes that occurred while the action was in flight. A future observable outcome contract must let agents distinguish success from invalidated completion without introducing a global EventBus.

## Does not own

Pathfinding, dynamic occupancy/reservation policy, long-range `MoveTo`, falling or locomotion families beyond the current adjacent walker.

## Deferred

Occupancy interaction, cancellation, observable completion outcome, involuntary falling and richer locomotion remain explicit future milestones.
