# Development Journal

The Development Journal preserves **history**, not current normative truth. It exists so experiments, visual observations, architecture audits and important development reasoning are not lost after the final implementation changes.

A Journal page may be completely accurate about an older point in time and still be outdated today. When you need to know how EvoForge works **now**, read [Project Context](../project-context.md), [Architecture](../architecture.md) and [Systems](../systems/) first.

## Journal sections

### Entries

`entries/` contains dated development narratives: what problem was being solved, what was tried and what changed during a work session/milestone.

### Design explorations

`design/` preserves deeper exploratory thinking that helped shape later systems. These records are useful for understanding abandoned constraints and earlier mental models, but they do not override the final System/ADR.

### Acceptance records

`acceptance/` records manual visual/behavior acceptance that cannot be honestly reduced to a unit test. An acceptance entry should state exactly what was inspected and what baseline was accepted.

### Audits

`audits/` contains point-in-time repository/system audits. Audits are especially useful before a large refactor because they separate **what exists now** from **what should become canonical next**.

## Standard metadata

Every Journal page should state:

```text
Type: Entry | Design exploration | Acceptance | Audit
Status: Historical record | Current acceptance baseline
Date: exact date, month, or "not recorded"
Normative: No
```

The body should distinguish observation, conclusion and the forward link to whatever became canonical.

## Truth order

If a Journal entry conflicts with modern implementation:

```text
production code + tests
        ↓
current normative docs
        ↓
accepted ADRs
        ↓
Journal
```

The Journal is intentionally last in this chain. Its value is remembering *how we arrived here*, not forcing current code to preserve every old idea.
