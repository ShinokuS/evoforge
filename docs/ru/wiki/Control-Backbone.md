# Control Backbone

Control Backbone — единая граница, через которую external intent попадает в authoritative simulation logic.

Этот слой намеренно остаётся маленьким. Он не является EventBus, internal RPC, заменой Scheduler и не требует представлять каждую world mutation в виде Command.

## Основной принцип

Command пересекает **external intent boundary**.

Типичные источники:

- player input;
- AI controllers;
- scripts и scenarios;
- network adapters;
- debug/admin tools.

После принятия intent продолжающиеся internal processes и internal state producers работают напрямую через narrow domain APIs authoritative systems.

```text
Player / AI / Script / Network
            |
            v
          Command
            |
            v
SynchronousCommandGateway
            |
            v
     CommandDispatcher
            |
            v
          Handler
            |
            v
       domain write API
```

World generation, erosion, уже запущенный Movement Action, mining process или другая internal mechanic не обязаны создавать Commands только ради вызова другой authoritative system.

Это не даёт Control превратиться в message bus, где каждая mutation скрыта за `ApplySomethingCommand`.

## Модель результатов

Все operation results имеют минимальный neutral contract:

```java
public interface OperationResult {
    boolean accepted();
    ResultCode code();
}
```

`ResultCode` namespaced, например:

```text
terrain:placed
terrain:position_occupied
movement:started
movement:already_moving
movement:transition_unavailable
```

Global enum со всеми причинами отказа нет.

`CommandResult` расширяет `OperationResult`, поэтому generic Control может видеть accepted/rejected и namespaced code без знания domain semantics.

Concrete domains при этом могут иметь richer typed results.

## Отказ или исключение

Граница фиксирована:

```text
conflict из-за current world state
    -> structured result

invalid programming/configuration input
    -> exception
```

Normal structured rejection:

- terrain position уже занята;
- у object нет ordinary movement capability;
- object не размещён;
- у object уже есть active Movement Action;
- requested destination не adjacent;
- Navigation не содержит requested directed transition.

Programming/configuration errors:

- null command/dependency;
- handler для command type не зарегистрирован;
- duplicate handler registration;
- handler вернул null;
- вызывающий передал unknown trusted runtime definition/object id;
- valid movement edge пришёл к broken traversal definition/configuration.

## Ожидания внутренних producers

Один domain result может быть normal rejection для одного caller и invariant failure для другого.

Player placement может штатно получить `terrain:position_occupied`. Deterministic world generator может, наоборот, требовать free position.

Internal producers выражают expectation generically:

```java
OperationResults.requireAccepted(
        landscape.placeTerrain(...));
```

Они не сравнивают результат с concrete success constant, если конкретное различие не входит в их logic.

`requireAccepted` не меняет contract domain operation; он только фиксирует expectation caller.

## Generic Command core

Generic-часть находится в:

```text
simulation/control/core/
```

Current types:

- `Command<R extends CommandResult>` — immutable intent marker;
- `CommandResult` — minimal observable result;
- `CommandHandler<C,R>` — typed execution boundary;
- `CommandDispatcher` — registration/dispatch по exact runtime class.

Dispatcher сам хранит небольшую registration map. Separate registry не вводится без реального requirement.

### Правило точного типа

Один concrete command class имеет один handler.

```text
PlaceTerrainCommand.class -> PlaceTerrainHandler
MoveStepCommand.class     -> MoveStepHandler
```

Dispatcher не ищет “ближайший” handler по superclass/interface.

Missing/duplicate registration — bootstrap/programming error, а не domain rejection.

## Семантика synchronous delivery

Current delivery implementation:

```text
simulation/control/sync/SynchronousCommandGateway
```

`submit(command)` немедленно dispatch-ит и вызывает handler.

Для immediate operation вроде accepted terrain placement mutation видна до возврата `submit`.

Для timed operation вроде Movement synchronous **command delivery** не означает synchronous **domain completion**:

```text
submit(MoveStepCommand)
    -> immediately validates and starts MovementAction
    -> returns movement:started
    -> Spatial position всё ещё source
    -> Scheduler завершает action позже в simulation time
```

Это важное разделение: Control delivery определяет, когда intent достигает domain, а domain определяет, завершается ли принятая работа сразу или становится long-lived process.

Порядок submitted Commands для deterministic caller остаётся порядком вызовов.

Future queued/asynchronous gateways смогут переиспользовать Command/Handler/Dispatcher, но обязаны явно определить queue order, flush point и state visibility.

## Закон зависимостей

Generic Control маршрутизирует commands, но не знает world domains.

Dependency direction:

```text
simulation.control.core  -X-> world.*
simulation.control.sync  -X-> world.*
world.*                   -X-> simulation.control.*
```

Concrete use-case adapters под `simulation/control/<use-case>/` могут импортировать narrow domain APIs, которые оркестрируют.

Current examples:

```text
control/terrain/PlaceTerrainHandler
        -> LandscapeMutations

control/movement/MoveStepHandler
        -> MovementSystem
```

Reverse dependency запрещена.

`ControlDependencyContractTest` проверяет generic package rules.

## Организация команд

Current command surface:

```text
simulation/control/
├── core/
├── sync/
├── terrain/
│   ├── PlaceTerrainCommand
│   ├── PlaceTerrainResult
│   └── PlaceTerrainHandler
└── movement/
    ├── MoveStepCommand
    ├── MoveStepResult
    └── MoveStepHandler
```

Concrete commands группируются по **intent/use-case**, а не обязательно по authoritative system, которую в итоге mutate-ят.

Future `BuildStructureCommand` относится к construction, даже если handler координирует Inventory, Objects, Spatial и Landscape.

## Terrain placement vertical slice

`PlaceTerrainCommand` проверяет immediate synchronous mutation path:

```text
PlaceTerrainCommand
        |
        v
PlaceTerrainHandler
        |
        v
LandscapeMutations.placeTerrain
        |
        v
TerrainSystem + Geometry lifecycle
        |
        v
PlaceTerrainResult
```

Expected behavior:

```text
first placement into empty position
    -> ACCEPTED / terrain:placed

second placement into same position
    -> REJECTED / terrain:position_occupied
    -> original terrain unchanged
```

## Timed Movement vertical slice

`MoveStepCommand` доказывает, что Command может запустить long-lived domain process, не превращая каждую внутреннюю phase в новую Command.

```text
MoveStepCommand
        |
        v
MoveStepHandler
        |
        v
MovementSystem.startStep
        |
        +--> validate capability / adjacency / Navigation
        +--> TransitionCost -> MovementRate -> duration
        +--> create MovementAction
        +--> ProcessScheduler.scheduleAfter(...)
        |
        v
MoveStepResult = movement:started

later, через Scheduler, а не Control:

MovementActionProcessor.complete(processId)
        |
        +--> revalidate object/source/Navigation
        |
        v
SpatialSystem.move(...) or interrupt
```

Этот slice фиксирует важные Control boundaries:

```text
Command несёт external start intent
accepted не означает immediate final mutation
continuing Action — domain state, а не поток internal Commands
Scheduler continuation обходит CommandDispatcher
Movement completion mutate-ит authoritative systems через domain APIs
```

Полная timing/cost semantics — в [Movement System](Movement-System.md).

## Граница мутаций Landscape

Terrain state и Geometry state — separate authoritative concerns, но некоторые lifecycle operations должны поддерживать coherence.

Public coordinated write capability:

```text
LandscapeMutations
```

Current terrain lifecycle:

```text
placeTerrain
    -> stale geometry override cleared
    -> default geometry FullShape

replaceTerrain
    -> geometry override preserved

removeTerrain
    -> geometry override cleared
```

`TerrainSystem` остаётся owner terrain storage/invariants и не зависит от Geometry.

`LandscapeSystem` координирует `TerrainSystem` и `GeometrySystem`, поэтому любой client `LandscapeMutations` получает одинаковую lifecycle semantics.

## Чек-лист новой команды

При добавлении новой Command:

1. убедиться, что она действительно пересекает external intent boundary;
2. создать immutable command в подходящем `control/<use-case>/`;
3. определить typed `CommandResult` с observable `accepted` и namespaced `code`;
4. реализовать один typed handler через narrow domain APIs;
5. зарегистрировать ровно один handler для concrete command class;
6. протестировать accepted/rejected world-state paths;
7. invalid programming/configuration inputs оставлять exceptions;
8. не обучать `CommandDispatcher` новому domain type;
9. если acceptance запускает long-lived process, хранить continuation в domain, а не возвращать её в Commands;
10. при изменении stable contract обновлять architecture/reference documentation.
