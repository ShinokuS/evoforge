# Development Workflow

EvoForge develops in small, reviewable vertical slices.

## Normal flow

```text
discuss semantic ownership and contract
    ↓
feature/refactor branch + draft PR
    ↓
implementation + headless tests
    ↓
diagnostics/manual observation where relevant
    ↓
document only semantic owners that actually changed
    ↓
CI
    ↓
manual acceptance when presentation/behavior needs it
    ↓
squash merge to main
```

Do not write large speculative frameworks before the first consumer. Do not defer a demonstrated correctness or performance problem merely because the project is early.

## Documentation rule

An implementation-only refactor does not require editing system documentation if its semantics remain unchanged. A new subsystem normally adds one system page. Architecture changes only for cross-system rules.

## Performance

When a regression appears, add/inspect lightweight telemetry, reproduce it and optimize the measured hot path behind existing contracts. Keep the measurement when it remains useful as a development guard.

## Branch discipline

`main` is protected by PR checks. Prefer draft PRs while semantics or manual visual acceptance are still active. Squash merge completed slices so main records the architectural unit rather than every implementation iteration.
