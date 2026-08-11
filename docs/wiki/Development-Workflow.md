# Development Workflow

EvoForge development is intentionally incremental. Architectural work is split into small semantic changes that can be reviewed, tested, and reverted independently.

## Normal change flow

```text
architectural question
    ↓
explicit scope
    ↓
feature branch
    ↓
test / implementation commits
    ↓
draft pull request
    ↓
local JVM tests
    ↓
review and correction
    ↓
full simulation suite
    ↓
final approval
    ↓
squash merge
```

The goal is to prevent unrelated cleanup or speculative architecture from hiding inside a feature change.

## One bounded problem at a time

A PR should have one clear semantic purpose.

Good examples:

```text
add local transition resolver
add production RampShape
harden Ramp endpoint and solid-volume semantics
add Wiki synchronization
```

Bad scope expansion looks like:

```text
fix Ramp
+ redesign Movement
+ add path cost
+ add caching
+ add diagnostics
+ reorganize packages
```

If a failing test exposes a deeper architectural problem, make that problem explicit before changing the scope.

## Test first for semantic defects

For behavior corrections, write the smallest test that states what must be true before changing production code.

This is particularly important for compositional systems such as Shape/Navigation where an apparently local change can alter unrelated edges.

After each topology-role change, rerun the most fragile regression tests immediately rather than waiting until the end of the PR.

## Commit structure

Commits should be semantic rather than formatter-driven. A useful pattern is:

```text
test(...): expose the missing invariant
fix(...): implement the minimal semantic correction
test(...): extend generic regression coverage
docs(...): update architecture and technical reference
```

Formatting-only differences should not drive architectural review.

The final GitHub merge is normally squash-merge, so intermediate branch commits can document the reasoning process without permanently cluttering `main` history.

## Branch naming

Current convention uses short feature-oriented names:

```text
feature/navigation
feature/ramp-shape
feature/ramp-hardening
feature/wiki-docs
```

A branch is disposable after its merged result reaches `main`.

## Pull request expectations

A useful PR description answers:

```text
What semantic behavior changes?
What intentionally does not change?
Which tests prove the behavior?
Which architectural boundary is preserved?
Are there deferred follow-ups?
```

Draft PRs are preferred while local JVM validation is still pending.

## Documentation discipline

Architecture changes update documentation in the same change set before merge.

The project distinguishes:

```text
docs/ARCHITECTURE.md        stable semantic contract
docs/TECHNICAL_REFERENCE.md current implementation reference
docs/wiki/                  explanatory long-form documentation
```

The Wiki should explain the reason and examples, while `ARCHITECTURE.md` remains concise enough to act as a review checklist.

## Local testing

Focused tests are useful during a change:

```bat
.\gradlew.bat :simulation:test --tests "*RampNavigationHardeningTest" --rerun-tasks --console=plain
```

Before merge, run the complete simulation suite:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

Avoid routine `clean`. Incremental Gradle builds are the normal workflow unless stale generated/build state is specifically suspected.

## External review

External review is used to challenge assumptions, not to automatically expand scope. A review finding becomes actionable when it identifies a concrete violated semantic rule or a failing scenario.

The preferred response is:

```text
review claim
    ↓
smallest reproducing test
    ↓
actual failure confirmed?
    ├── no  -> document why
    └── yes -> minimal correction
```

This prevents architectural churn caused by plausible but unverified reasoning.

## Deferred work is recorded, not half-built

When a real concern is intentionally postponed, record it clearly in architecture/technical documentation instead of adding partial infrastructure.

Examples currently include:

```text
geometry override lifecycle
navigation cache policy
path cost model
falling ownership
chunk/world-generation semantics
actor capabilities
```

A deferred decision should leave the existing public boundary capable of supporting the later solution.

## Wiki changes

Wiki pages are authored in `docs/wiki/` and reviewed like code. After changes reach `main`, the `sync-wiki.yml` workflow publishes the directory to the GitHub Wiki repository.

This makes the main repository the source of truth and gives documentation the same review/history discipline as production code.
