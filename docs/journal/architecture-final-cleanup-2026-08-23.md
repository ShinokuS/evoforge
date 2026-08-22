# Architecture final cleanup — 2026-08-23

This branch performs the final semantic-capability audit before Continuum Stage 5.

Confirmed review targets:

- add ArchUnit bytecode dependency checks and world-slice cycle detection;
- remove the ambiguous sibling `world/space` / `world/spatial` split;
- dismantle the residual `world/landscape` umbrella;
- replace the generic `world/surface` root with a precise sky-exposure capability home;
- reconcile stale package-navigation documentation and any additional dependency/ownership problems exposed by the bytecode audit.

Stage 5 remains not started until this cleanup is merged and its exact-head CI/scale/docs gates are green.
