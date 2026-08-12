# Adding a Mechanic

Use this checklist when a real gameplay/simulation need introduces a new mechanic.

## 1. Name the fact it owns

Write one sentence: **“X is the authoritative owner of …”**. If another system already owns the same fact, resolve that conflict before coding.

## 2. Separate immutable definition data from runtime state

If content configures the mechanic, compile a mechanic-owned immutable definition lookup. Do not add mutable runtime state to definitions or generic objects.

## 3. Define narrow reads and writes

Consumers should receive read-only lookup capabilities. Mutation stays with the owner or a semantic coordinator when one logical operation must update multiple authoritative owners.

## 4. Decide whether Command is involved

Use Command when the operation crosses the external-intent boundary. Internal scheduled continuation normally calls the mechanic directly.

## 5. Add headless tests before broad integration

Cover successful semantics, expected rejection, ownership/invariants and interactions with existing contracts. Add a structural architecture test when a dependency rule is important and cheaply executable.

## 6. Add diagnostics

Choose the cheapest way to observe correctness: scenario query, debug overlay, inspector, telemetry or explicit outcome. Diagnostics must observe simulation truth rather than reimplement it.

## 7. Document the new owner

Create `docs/systems/<mechanic>.md`. Do **not** edit unrelated completed system pages just to announce the new mechanic. Edit an existing page only if that system’s own semantics changed.

Update `architecture.md` only if the mechanic deliberately changes a global rule.
