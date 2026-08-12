# Control and Commands

## Purpose

Provide one external-intent boundary for player input, AI, scripts and scenarios without making generic Control depend on world-domain semantics.

## Command model

`Command<R extends CommandResult>` describes intent. The command object does not mutate the world itself. Exact-type handler registration routes the command to the domain capability that owns its meaning.

Expected domain rejection is represented by structured result data. Programming/configuration/invariant failures remain exceptions.

## Boundary

```text
external caller
    ↓
CommandGateway / dispatcher
    ↓ exact command type
registered handler
    ↓
domain mutation capability
```

Generic Control routing does not import world-domain types. World-domain packages do not depend on Control.

## Internal work is not Command RPC

A continuing Movement action, future world generator or other internal authoritative producer may call its narrow domain capability directly. Commands are for crossing the external-intent boundary, not for every mutation inside the simulation.

## Current transport

Command submission is synchronous. This is the current transport, not semantic identity; a future queued gateway must explicitly preserve or redefine deterministic ordering and within-tick visibility.

See [Command Boundary decision](../decisions/003-command-boundary.md).
