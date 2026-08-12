# Decision 003 — Commands are external intent

**Status:** Accepted

## Problem

Player input, AI, scripts and scenarios need one controllable entry point, but forcing every internal state transition through Commands would turn Control into an internal RPC/event framework.

## Decision

Commands cross the external-intent boundary. Generic Control routes exact command types to registered handlers and observes structured results. Internal continuing processes call narrowly owned domain capabilities directly.

## Consequences

- player/AI/script callers can share semantics;
- Control stays independent of world-domain types;
- scheduled Movement completion remains Movement logic rather than a synthetic command;
- a future asynchronous transport can replace synchronous submission only with an explicit ordering contract.
