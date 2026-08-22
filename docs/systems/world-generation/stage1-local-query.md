# Stage 1 — Local Query + Shared Region Cache

## Plain-language model

A consumer asks for a small local area. The query layer finds which technical regions cover that area. When several consumers need the same regions, those regions are calculated once and reused. Each consumer receives only its requested local window, not the whole shared cache.

```text
consumer requests
      ↓
unique technical regions
      ↓
calculate/load each region once
      ↓
clip a small local result for each consumer
```

The shared cache is an optimization and never authoritative world state.

## Revision rule

Every query targets a specific world revision. Moving to a newer revision replaces the reusable regional cache. Work that began against an older revision is never returned as current data.

## Concurrency rule

If concurrent callers request the same missing technical region, one materialization runs and the other callers share that in-progress result. Different regions remain free to materialize independently.

## Performance proof

The Stage 1 scale profile uses a 1,000,000 × 1,000,000 logical world and compares 1, 10 and 100 consumers requesting the same 32 × 32 local area spanning four technical pages. The expensive source field must still materialize exactly four pages in every case.

## Manual acceptance

The Stage 1 Inspector view must make four ideas obvious without internal terminology:

- blue = what consumers ask for;
- green = areas calculated once and shared;
- reused count = duplicated regional work avoided;
- each consumer result is only its requested area.

Stage 2 must not begin until this behavior is manually accepted.
