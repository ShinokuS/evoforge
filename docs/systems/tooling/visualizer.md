# Visualizer and Developer Inspection Tools

## Purpose

The Visualizer is an observer. It may display simulation or Continuum diagnostics, but it never decides what is physically true.

## Current F2 screen — Stage 1

`F2` currently opens the **Stage 1 shared-local-query proof**.

It is intentionally simple:

- **BLUE** — an area requested by one consumer;
- **GREEN** — a technical region calculated once and reused;
- **YELLOW** — one consumer's returned local area; nothing outside that area is exposed to that consumer.

The screen shows:

- number of consumers asking now;
- total region uses without sharing;
- unique regions actually calculated;
- repeated region calculations avoided;
- actual new page loads;
- current world revision.

Controls:

```text
1        one consumer
2        ten consumers
3        one hundred consumers
Arrows   move the example
R        advance world revision
Home     center
+/-      drawing scale only
Esc      back
```

The expected proof is easy to see: 1, 10 and 100 strongly overlapping consumers still require the same four unique technical regions. More consumers increase local returned data, but do not multiply the expensive shared regional calculation.

## Important boundary

The shared cache is not world truth. Consumers never receive whole shared pages directly; each receives only its own bounded local view.

Camera and drawing scale are presentation only.

## Previous Continuum inspector

The earlier page/cache and multi-resolution inspector remains in code because that accepted support work is still useful. During Stage 1, `F2` is deliberately routed to the current Stage 1 proof so manual acceptance always matches the stage being reviewed.

## Runtime observer boundary

Ordinary runtime visualization continues to read production simulation capabilities. Real user actions go through production command/domain paths; presentation does not mutate authoritative owners directly.

## Manual acceptance rule

A stage with spatial meaning is not complete only because tests are green. The user must be able to open the current proof and understand what the system is doing without reading internal class names.

See [Stage 1 — Local Query + Shared Region Cache](../world-generation/stage1-local-query.md) and [Continuum Development Plan](../world-generation/continuum-development-plan.md).
