# Testing Strategy

EvoForge favors fast headless tests around semantic contracts.

## Test what the system owns

- owner lifecycle and invariants;
- structured rejection for expected impossibility;
- cross-system contract boundaries;
- deterministic timing/order;
- geometry/navigation/cost results;
- cache correctness where caches derive authoritative reads.

## Structural architecture tests

Source/reflection tests are appropriate for simple rules whose accidental violation would be costly, such as:

- simulation/presentation constructor capability boundary;
- Control/world dependency direction;
- generic consumers not importing concrete Shape classes.

They are not replacements for semantic tests; they make architecture regressions fail immediately.

## Presentation

Pure presentation math and deterministic resolution should be unit-tested. Aesthetic choices such as palette balance and line weight require manual visual acceptance until a product-level screenshot/golden-image need justifies that tooling.

## Performance

Performance changes need representative telemetry or a benchmark-shaped workload. A faster implementation must preserve the same semantic tests.
