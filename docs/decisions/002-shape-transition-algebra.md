# Decision 002 — Shape contribution algebra

**Status:** Accepted for current geometry model

## Problem

Navigation must support different terrain Shapes without knowing every concrete Shape or letting one Shape query and interpret its neighbor.

## Decision

Each Shape contributes independent local departure, arrival and block masks. Generic composition resolves:

```text
resolved = departures & arrivals & ~blocks
```

Source and destination Shapes own only their side of the relationship. Intrinsic traversal factors follow the same departure/arrival role split.

## Consequences

- `NavigationSystem` does not switch on concrete Shape type;
- Shape processing order does not define topology;
- adding a Shape that fits the contract adds implementation/tests rather than branches in existing consumers;
- future Shapes that genuinely violate the one-supported-position model must drive a contract revision instead of an escape hatch.
