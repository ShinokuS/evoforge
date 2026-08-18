# Systems

EvoForge is easier to understand when its systems are grouped by the kind of question they answer instead of being presented as one long alphabetical list.

- **Foundations** — how simulation state, time, definitions, coordinates and structural geometry work.
- **Movement & navigation** — how actors discover valid routes, reserve space and move through the world.
- **Agents & life** — how autonomous agents perceive needs, choose goals and consume/regrow finite resources.
- **Environment** — terrain, liquids, Water, Soil and atmospheric surface hydrology.
- **World generation** — how a deterministic world is authored, calibrated, generated, materialized and handed to runtime.
- **Tools & diagnostics** — visual observation, generated-world audits and explicit warm-up tools.

Every system page follows the same reading order after the Stage 0 documentation audit: a plain-language explanation first, then the exact current model, algorithms/invariants, code ownership, limitations, tests and sources.

If you are returning to the project after time away, start with [Project Context](../project-context.md), then [Architecture](../architecture.md), then the relevant group below.
