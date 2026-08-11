# Стратегия тестирования

EvoForge относится к тестам как к исполняемой архитектуре. Подсистема не считается завершённой только потому, что работают example scenarios: тесты должны кодировать законы, не позволяющие будущим mechanics незаметно нарушать установленные boundaries.

## Цели тестирования

Suite решает разные задачи:

```text
local correctness
architectural invariants
cross-system integration
regression protection
reference/property validation
boundary safety
```

Один стиль тестов не заменяет остальные.

## Unit tests

Unit tests проверяют одну abstraction изолированно.

Примеры:

```text
ObjectId slot/generation representation
DefinitionId validation
TransitionMask bit mapping
TransitionPorts packing
TransitionComposition algebra
FullShape local topology
RampShape orientation symmetry
TerrainStorage behavior
```

Тест должен быть достаточно точным, чтобы broken local contract был понятен без построения целого World.

## Integration tests

Integration tests проверяют ownership boundaries и composition systems.

```text
Definition source -> compiled catalog -> ObjectFactory
Terrain -> Geometry
Geometry -> Navigation
terrain mutation -> next Navigation query
Shape pairs -> resolved edges
SpatialSystem -> reverse CellSpatialIndex
```

Особенно ценны случаи, где две systems должны оставаться ignorant concrete implementation друг друга.

## Тесты архитектурных инвариантов

Некоторые behaviors важнее отдельных examples и должны быть reusable invariants.

Текущие/планируемые:

```text
stale ObjectId never resolves to a reused object
terrain absence never exposes default Full geometry
solid terrain cells are not normal navigation positions
Shape role contributions obey the current standing-position contract
Navigation never emits the center direction
Navigation never emits directions outside the 26-neighbor mask
composition is independent of Shape processing order
missing endpoint support removes the corresponding structural edge
```

Цель — ловить целый класс ошибок, а не воспроизводить один bug.

## Ramp hardening scenarios

Ramp тестируется через полные topology scenarios, потому что role bugs легко пропустить в local Shape tests.

```text
lower Full <-> Ramp <-> upper Full
missing upper Full -> no upper edge
missing lower Full -> no lower ascent or descent edge
consecutive Ramp chain
side entry blocked
Ramp terrain body non-navigable
Full occupying a transition destination blocks ascent
```

Reverse traversal проверяется независимо; bidirectionality не предполагается из forward edge.

## Reference resolver tests

`NavigationReferencePropertyTest` сравнивает production resolver с намеренно более простой реализацией на deterministic randomized geometry mutations.

Reference implementation не должна копировать optimized loop line-for-line. Её ценность в независимости: одинаковая structural mistake по одной причине создаёт false confidence.

Reference test включает:

```text
FullShape
all four RampShape orientations
synthetic table-driven Shapes
random departure masks
random arrival masks
random block masks
near and distant mutations
```

Fixed seed делает failure reproducible; message содержит seed, mutation step и source XYZ.

## Deterministic tests

Randomized tests всё равно должны быть deterministic: explicit seeds и stable iteration/tie-breaking. Тест, который “обычно проходит”, не является доказательством deterministic simulation.

## Negative-space tests

Нужно проверять не только существующее, но и то, чего **не должно** быть:

```text
no free Full-to-Full vertical step
no edge into an unsupported void
no Ramp side entry
no walk through solid terrain
no false edge after endpoint removal
no coordinate wrap neighbor
```

Это особенно важно в compositional systems, где OR-accumulation может случайно собрать valid-looking pair bits из unrelated contributors.

## Boundary tests

Public coordinates — `int`. Local arithmetic рядом с integer extremes тестируется против wrap. Это implementation-safety, а не world-size requirement.

Future chunk/region boundaries получат такие же edge tests после фиксации semantics.

## Test-first hardening workflow

При подозрении на архитектурный defect:

```text
1. выразить ожидаемую семантику минимальным failing test;
2. запустить его на current production code;
3. подтвердить реальную причину failure;
4. внести минимальное production change по контракту;
5. после каждой role/topology правки прогнать соседние regressions;
6. перед merge прогнать полный simulation suite.
```

Так architecture не “чинится” одной интуицией.

## Что не стоит слишком рано делать permanent invariant

Полезная текущая convention не обязательно forever-contract.

Например, production Shapes сейчас имеют одну standing position `anchor + (0,0,1)`. Это достаточно сильно для current tests и вывода Navigation read window, но не должно запрещать любую будущую geometry без consumer evidence.

Тесты должны различать:

```text
FIXED semantic invariant
CURRENT production-shape contract
implementation detail
```

## Запуск тестов

Полный simulation suite:

```bash
./gradlew :simulation:test --rerun-tasks --console=plain
```

Windows:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

Для focused development используйте Gradle `--tests`, затем перед final review возвращайтесь к полному module suite.
