# Добавление Shape

Это руководство описывает, как добавить новую terrain geometry implementation без coupling Navigation, Movement или TransitionCost calculation к новому concrete type.

## До добавления Shape

Убедитесь, что требование действительно относится к новой geometry, а не к actor ability, occupancy rule, temporary traversal state, material property или AI preference.

Shape может описывать два вида **intrinsic local geometry**:

```text
structural topology
    -> departures / arrivals / blocks

actor-independent directed traversal geometry
    -> departureTraversalFactor / arrivalTraversalFactor
```

Shape **не должен** кодировать:

```text
which actor is moving
whether this actor can climb/swim/fly
whether another object occupies the destination
terrain material traversal.cost
actor-specific surface affinity
AI preference
falling policy
world-neighbor queries
```

Intrinsic Shape traversal factor допустим только тогда, когда сама geometry действительно создаёт directed cost effect, который уже не выражен grid direction length. Это не место для скрытого actor policy.

## Шаг 1: описать geometry семантически

До Java запишите local topology в координатах.

Например текущий positive-Y Ramp можно описать standing positions:

```text
lower A
    ↗
ramp B
    →
upper C
```

с directed edge deltas:

```text
A -> B = (0,+1,+1)
B -> A = (0,-1,-1)
B -> C = (0,+1,0)
C -> B = (0,-1,0)
```

Movement deltas должны оставаться в 26-neighbor transition space.

Также определите, нужен ли geometry какой-либо intrinsic traversal multiplier сверх grid displacement. Не добавляйте multiplier только потому, что Shape «особенный». Текущий Ramp использует neutral factors, потому что его elevation/distance уже выражены фактическим edge direction и `GridTransitionLength`.

## Шаг 2: определить ownership каждого directed edge

Для каждого directed edge определите, какой Shape поддерживает source standing position и какой — destination standing position.

Текущее production Shape role convention:

```text
source-supporting Shape      -> departure
source-supporting Shape      -> departure traversal factor

destination-supporting Shape -> arrival
destination-supporting Shape -> arrival traversal factor

solid obstruction            -> block
```

Topology и traversal cost используют один и тот же ownership law.

Не позволяйте одному Shape давать обе роли внешнего edge только ради passing test. Иначе появляется phantom topology при отсутствии настоящего endpoint Shape, а traversal price начинает назначаться geometry, которой этот Shape не владеет.

## Шаг 3: реализовать только local declarations

Текущий Shape API:

```java
long transitionPorts(
        int relativeX,
        int relativeY,
        int relativeZ);

int transitionBlocks(
        int relativeX,
        int relativeY,
        int relativeZ);

int departureTraversalFactor(
        int relativeX,
        int relativeY,
        int relativeZ,
        int directionX,
        int directionY,
        int directionZ);

int arrivalTraversalFactor(
        int relativeX,
        int relativeY,
        int relativeZ,
        int directionX,
        int directionY,
        int directionZ);
```

Relative source coordinates всегда используют terrain anchor Shape как origin. Direction — это immediate edge от этого source к destination.

Shape не должен выполнять world lookup или проверять neighboring concrete Shape types.

Хорошо:

```text
if source is at my supported position, offer these departures
if source is at this relative mouth position, confirm this arrival
if I own this departure role, contribute my local directed factor
if I own this arrival role, contribute my local directed factor
```

Плохо:

```text
if neighbor is FullShape, allow edge
if world has terrain there, allow edge
if Navigation says endpoint exists, allow edge
if mover is a horse, use factor 800
if destination terrain is mud, use factor 1500
```

## Шаг 4: использовать default traversal factors, пока geometry не докажет обратное

Default Shape implementation выводит factor ownership напрямую из `transitionPorts`:

```text
owned role -> ShapeTraversalFactor.NEUTRAL = 1000
not owned  -> ShapeTraversalFactor.NONE    = 0
```

Поэтому большинству Shapes вообще не нужно override traversal factors.

Override factor нужен только тогда, когда intrinsic geometry имеет стабильный actor-independent effect, который уже не выражен `GridTransitionLength`.

Корректный override должен сохранять role check и менять только positive factor для roles, которыми Shape действительно владеет. Концептуально:

```text
base = Shape.super.departureTraversalFactor(...)
if base == NONE:
    return NONE
return desiredPositiveFactor
```

и аналогично для arrival.

Никогда не возвращайте positive traversal factor для topology role, которого Shape не предоставляет.

## Шаг 5: учитывать solid volume, когда это применимо

Если terrain body solid, ordinary entry через occupied volume должен блокироваться.

`FullShape` и `RampShape` используют `SolidCellBlocking`. Переиспользуйте его только при той же solid-cell obstruction semantics. Будущий Shape с другим physical volume не обязан подстраиваться под этот helper.

## Шаг 6: unit-test local topology

Сначала тестируйте Shape напрямую.

Полезные assertions:

```text
exact departure mask from supported position
exact arrival mask from each supported mouth/source position
no unexpected side ports
orientation symmetry
expected block mask around occupied volume
no ports outside intended local domain
```

Для rotated instances докажите, что каждая orientation имеет ту же topology после rotation/sign change.

## Шаг 7: тестировать traversal-factor ownership

Каждый production Shape должен сохранять cost-role ownership согласованным с topology-role ownership.

Как минимум проверьте:

```text
no departure port -> departure factor NONE
departure port + neutral geometry -> departure factor NEUTRAL
no arrival port -> arrival factor NONE
arrival port + neutral geometry -> arrival factor NEUTRAL
```

Если Shape использует non-neutral factor, отдельно тестируйте exact directed roles и reverse direction.

Также добавьте TransitionCost-level test, который доказывает, что factor меняет cost без появления concrete-type branch в `TransitionCostCalculator`.

## Шаг 8: тестировать отсутствие endpoints

Integration tests должны независимо удалять каждый endpoint.

Для connector:

```text
A <-> NewShape <-> C
```

проверьте:

```text
missing A -> neither direction of the A connector survives
missing C -> neither direction of the C connector survives
```

Это ловит наиболее опасный composition bug: Shape голосует одновременно departure и arrival за edge, который должен зависеть от другого Shape.

Поскольку TransitionCost рассчитывается только для Navigation-valid edges, отсутствие endpoint topology должно удалить edge до попытки cost calculation.

## Шаг 9: тестировать chains

Если geometry соединяется с самой собой, добавьте direct chain test. Ramp chaining уже выявлял role mistakes, которые не проявлялись в простом Full/Ramp/Full scenario.

Chain должен использовать canonical world placement, а не artificial intermediate cells.

Если Shape имеет non-neutral factor, при необходимости проверьте repeated-chain cost/timing behavior, а не только isolated edge.

## Шаг 10: запускать generic contract tests

Current production Shapes должны соблюдать single-standing-position role law, описанный в [Контракте Shape](Shape-Contract.md).

Новый production Shape должен участвовать в generic tests:

```text
terrain body non-navigation when solid
role-law consistency
traversal-factor role consistency
reference Navigation composition
no center-bit leakage
no unexpected endpoint edges
```

Если новый Shape по законной semantic reason не может соблюдать текущий role law, не добавляйте local exception. Пересмотрите вместе:

```text
Shape contract
Navigation resolver read window
TransitionCost support-owner lookup
```

Все три вытекают из одной supported-position model.

## Шаг 11: central systems должны оставаться независимыми от concrete type

Добавление Shape никогда не должно приводить к коду:

```java
if (shape instanceof NewShape) {
    ...
}
```

внутри:

```text
NavigationSystem
MovementSystem
TransitionCostCalculator
```

Также не должно появляться central registry, сопоставляющее каждый concrete Shape class с cost logic.

Generic resolver/calculator может измениться, если существующий Shape contract доказанно недостаточен, но причиной должен быть semantic contract, а не имя concrete class.

## Шаг 12: понимать, что не относится к Shape cost

У текущего `TransitionCost` есть отдельные owners:

```text
LandscapeDefinitionId traversal.cost
    -> material/surface contribution

Shape traversal factor
    -> intrinsic local geometry contribution

GridTransitionLength
    -> discrete direction length

MovementRate
    -> mover speed / cost-to-time conversion
```

Не дублируйте смысл одного owner в другом.

Примеры:

```text
mud is slow material
    -> landscape traversal.cost

edge changes two grid axes
    -> GridTransitionLength

stairs intrinsically require more geometric effort
    -> possibly Shape factor, if actor-independent and proven

horse is faster than human
    -> MovementRate

wheels dislike stairs but legs do not
    -> future actor/geometry interaction, NOT universal Shape factor
```

## Шаг 13: обновить документацию

Когда Shape становится production behavior, обновите:

```text
docs/TECHNICAL_REFERENCE.md
Shape Contract / geometry pages
Movement System when traversal semantics change
ARCHITECTURE.md only if stable semantic contract changes
EN/RU counterparts and i18n freshness hashes
```

Не дублируйте каждый implementation detail в `ARCHITECTURE.md`; этот файл должен оставаться нормативным и компактным.

## Checklist готовности

Production Shape готов, когда:

```text
intended topology tested
reverse topology tested
missing endpoints tested
solid volume tested when applicable
orientations/chains tested
role invariants tested
traversal factors aligned with topology roles
non-neutral factor tested through TransitionCost when applicable
no central concrete-type branch added
full simulation suite green
EN/RU documentation synchronized
```
