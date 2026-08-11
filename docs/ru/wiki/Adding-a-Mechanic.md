# Добавление механики

Новая mechanic — новый domain авторитетного behavior/state, а не просто новое content value. Это руководство объясняет, как добавить её без превращения центральных классов в universal state containers.

## Сначала решить: content или mechanic?

Если существующие mechanics уже выражают нужное behavior, добавляйте только definition data.

```text
new rock material with existing physical properties
    -> data

new independent temperature state that changes over time
    -> mechanic
```

Не создавайте runtime system для каждого content type и не добавляйте поле в `WorldObject`/universal cell только потому, что новому значению нужно storage.

## Определить authoritative ownership

До выбора classes ответьте:

> Какими изменяемыми фактами уникально владеет эта mechanic?

Примеры:

```text
SpatialSystem    owns ObjectId -> XYZ
TerrainSystem    owns XYZ -> LandscapeDefinitionId | absence
```

У новой mechanic должно быть столь же ясное ownership statement. Если его нельзя сформулировать, boundary ещё не готова.

## Определить narrow read contract

Consumers обычно зависят от read-only interface, а не mutable system implementation.

Pattern:

```text
MechanicSystem   authoritative mutation
MechanicLookup   narrow read access
MechanicState    optional internal storage
```

Точная class split не обязательна; ясность ownership/dependency — обязательна.

## Интеграция definitions

Если mechanic имеет immutable per-definition configuration, добавьте mechanic-specific definition compiler/store вместо расширения universal definition object.

```text
source JSON aspect
    ↓
DefinitionAspectCompiler
    ↓
mechanic-owned compiled definition data
    ↓
runtime system references typed DefinitionId
```

Registration explicit; reflection и giant central switch не нужны.

## Избегать reverse dependencies

Mechanic может читать другую subsystem через narrow lookup, если dependency семантически one-way.

Не создавайте mutual dependencies для implicit lifecycle coordination. Если два owners должны меняться atomically, обычно нужен orchestration/command layer выше обоих.

## Mutation и events

Authoritative mutation выполняется owner. Event, если есть, описывает факт **после** mutation.

Не делайте events hidden commands, которые другие systems обязаны обработать, чтобы исходная mutation стала valid.

## Scheduler integration

Если mechanic нужна future activation, schedule registered handler. Scheduler не должен знать domain type механики сверх generic handler/task contract.

Избегайте mandatory per-tick scans всех objects/terrain cells. По возможности schedule только active processes.

## Spatial queries

Если mechanic нужны только object positions, используйте existing spatial boundaries.

Domain-specific index принадлежит mechanic как derived state. Не добавляйте unrelated query structures в `SpatialSystem` только из-за координат.

## Тестирование

Новая mechanic включает:

```text
owner unit tests
read-contract tests
mutation invariant tests
definition compilation/loading tests when applicable
integration tests with each consumed boundary
determinism tests when ordering/randomness matters
lifecycle tests for create/remove/recreate paths
```

Для performance-sensitive mechanic сначала нужен representative functional workload, затем benchmark/optimization после стабилизации semantics.

## Производительность

Начинайте с самой ясной реализации, соблюдающей scale envelope. Если profiling позже показывает allocation/lookup pressure, internal storage меняется за существующей semantic boundary.

Не выносите packed keys/specialized arrays в public contracts, если это не domain semantics.

## Commands и Control

После появления Control Backbone внешние изменения входят через Commands, а не прямую mutation mechanic из presentation, AI или scripts.

```text
Player / AI / Script / Scenario
            ↓
         Command
            ↓
       orchestration
            ↓
      MechanicSystem
```

Особенно важно для actions, координирующих несколько authoritative owners.

## Документация

Обновляйте соответствующий уровень:

```text
ARCHITECTURE.md        if a stable boundary/invariant changes
TECHNICAL_REFERENCE   for implementation/package/test details
Wiki                  for explanatory design and extension guidance
Russian counterpart   when the English source changes
```

Не создавайте speculative Wiki pages для ещё не существующих systems; deferred decisions живут в [Дорожной карте](Roadmap-and-Deferred-Decisions.md).
