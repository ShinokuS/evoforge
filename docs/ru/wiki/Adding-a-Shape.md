# Добавление Shape

Руководство по добавлению новой terrain geometry без coupling Navigation к новому concrete type.

## До добавления Shape

Убедитесь, что требование действительно относится к geometry, а не к movement ability или временной traversal rule.

Shape должен описывать structural terrain topology. В него не входят:

```text
which actor is moving
whether the actor can climb
whether another object occupies a destination
path cost
AI preference
falling
terrain material identity
world-neighbor queries
```

## Шаг 1: описать geometry семантически

До Java запишите local topology в координатах.

Например positive-Y Ramp:

```text
lower A
    ↗
ramp B
    →
upper C
```

Directed edge deltas:

```text
A -> B = (0,+1,+1)
B -> A = (0,-1,-1)
B -> C = (0,+1,0)
C -> B = (0,-1,0)
```

Movement deltas должны оставаться в 26-neighbor transition space.

## Шаг 2: определить владельцев каждого directed edge

Для каждого edge спросите: какой Shape поддерживает source standing position и какой — destination standing position?

Текущее production role convention:

```text
source-supporting Shape      -> departure
destination-supporting Shape -> arrival
solid obstruction            -> block
```

Не позволяйте одному Shape давать обе роли внешнего edge только ради passing test. Иначе появляется phantom topology при отсутствии настоящего endpoint Shape.

## Шаг 3: реализовать только local declarations

Shape получает только relative source coordinates:

```java
long transitionPorts(int relativeX, int relativeY, int relativeZ)
int transitionBlocks(int relativeX, int relativeY, int relativeZ)
```

Он не выполняет world lookup и не проверяет соседние Shape types.

Хорошо:

```text
if source is at my standing position, offer these departures
if source is at this relative mouth position, accept this arrival
```

Плохо:

```text
if neighbor is FullShape, allow edge
if world has terrain there, allow edge
if Navigation says the endpoint exists, allow edge
```

## Шаг 4: учесть solid volume

Если terrain body solid, вход через occupied volume должен блокироваться.

`FullShape` и `RampShape` используют `SolidCellBlocking`. Переиспользуйте его только при той же solid-cell obstruction semantics. Будущий Shape с другим physical volume не обязан подстраиваться под этот helper.

## Шаг 5: unit tests local topology

Сначала тестируйте Shape напрямую.

Полезные assertions:

```text
exact departure mask from standing position
exact arrival mask from each supported mouth/source position
no unexpected side ports
orientation symmetry
expected block mask around occupied volume
no ports outside the intended local domain
```

Для rotated instances докажите эквивалентность topology при rotation/sign change.

## Шаг 6: тестировать отсутствие endpoints

Integration tests должны независимо удалять каждый endpoint.

Для:

```text
A <-> NewShape <-> C
```

проверить:

```text
missing A -> neither direction of the A connector survives
missing C -> neither direction of the C connector survives
```

Это ловит опасный bug, когда Shape голосует departure+arrival за edge, который должен зависеть от другого Shape.

## Шаг 7: тестировать chains

Если geometry соединяется с самой собой, нужен direct chain test. Ramp chaining выявил role mistakes, которые не проявлялись в Full/Ramp/Full.

Chain должен использовать canonical world placement без artificial intermediate cells.

## Шаг 8: запустить generic contract tests

Current production Shapes должны соблюдать single-standing-position role law из [Контракта Shape](Shape-Contract.md).

Новый Shape также должен участвовать в tests:

```text
terrain body non-navigation when solid
role-law consistency
reference Navigation composition
no center-bit leakage
no unexpected endpoint edges
```

Если Shape по законной семантической причине не может соблюдать текущий role law, не добавляйте local exception. Пересмотрите Shape contract и resolver read window вместе.

## Шаг 9: Navigation остаётся type-agnostic

Добавление Shape никогда не должно приводить к:

```java
if (shape instanceof NewShape) {
    ...
}
```

внутри `NavigationSystem`.

Generic resolver может измениться, если существующий Shape contract доказанно недостаточен, но причина должна быть в контракте, а не в concrete class.

## Шаг 10: обновить документацию

Когда Shape становится production behavior, обновить:

```text
docs/TECHNICAL_REFERENCE.md
relevant Wiki pages
ARCHITECTURE.md only if a stable semantic contract changes
```

Для русского раздела также обновляется соответствующая страница `docs/ru/` в том же PR.

## Checklist готовности

Production Shape готов, когда протестированы intended topology, reverse topology, missing endpoints, solid volume, orientations, chains и generic role invariants, а полный simulation suite зелёный.
