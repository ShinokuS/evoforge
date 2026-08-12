# Time and Scheduling

## Purpose

Provide deterministic discrete simulation time and generic scheduling infrastructure without embedding domain semantics in the scheduler.

## Ownership

`SimulationClock` owns the current simulation tick. `SimulationTime` is its read-only capability.

`Scheduler` owns scheduled activation ordering. `ProcessScheduler` / bound scheduling infrastructure associates domain-owned process identity with scheduled callbacks without taking ownership of the process state itself.

## Semantics

- simulation advances in discrete ticks;
- rendering FPS is unrelated to authoritative time;
- scheduled ordering is deterministic for the same inputs/order of mutation;
- scheduler callbacks delegate to registered domain handlers;
- domain systems decide what completion means.

## `SimulationStepper`

`SimulationStepper` is the small production capability that advances simulation in deterministic ticks. The visualizer may call it through `VisualizerTimeController` for pause/run/step controls without receiving broad mutation access.

## Does not own

Movement semantics, AI decisions, command routing or presentation frame timing.

## Deferred

Background authoritative scheduling and multithreaded mutation require an explicit future ordering/snapshot contract.
