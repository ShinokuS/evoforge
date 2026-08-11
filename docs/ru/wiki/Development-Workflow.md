# Процесс разработки

Разработка EvoForge намеренно инкрементальна. Архитектурная работа делится на небольшие semantic changes, которые можно независимо review, test и revert.

## Обычный flow изменения

```text
architectural question
    ↓
explicit scope
    ↓
feature branch
    ↓
test / implementation commits
    ↓
draft pull request
    ↓
CI / local JVM tests
    ↓
review and correction
    ↓
full simulation suite
    ↓
final approval
    ↓
squash merge
```

Цель — не прятать unrelated cleanup или speculative architecture внутри feature change.

## Одна ограниченная проблема за раз

PR должен иметь одну ясную semantic purpose.

Хорошо:

```text
add local transition resolver
add production RampShape
harden Ramp endpoint and solid-volume semantics
add multilingual documentation
```

Плохо:

```text
fix Ramp
+ redesign Movement
+ add path cost
+ add caching
+ add diagnostics
+ reorganize packages
```

Если failing test раскрывает более глубокую architectural problem, сначала явно изменить scope.

## Test first для semantic defects

При correction поведения сначала пишется минимальный test, формулирующий требование, и только потом production code.

Особенно важно для compositional systems Shape/Navigation, где локальная правка может поменять unrelated edges.

После role/topology change сразу запускать самые хрупкие regression tests.

## Структура commits

Commits должны быть semantic, а не formatter-driven. Полезный pattern:

```text
test(...): expose the missing invariant
fix(...): implement the minimal semantic correction
test(...): extend generic regression coverage
docs(...): update architecture and technical reference
```

Финальный GitHub merge обычно squash, поэтому intermediate commits могут сохранять reasoning branch без засорения `main`.

## Branch naming

Текущая convention:

```text
feature/navigation
feature/ramp-shape
feature/ramp-hardening
feature/wiki-docs
feature/docs-i18n-ru
```

Branch disposable после merge результата в `main`.

## Ожидания от pull request

PR description отвечает:

```text
What semantic behavior changes?
What intentionally does not change?
Which tests prove the behavior?
Which architectural boundary is preserved?
Are there deferred follow-ups?
```

Draft PR удобен, пока validation ещё не завершена.

## Документационная дисциплина

Architecture changes обновляют docs в том же change set до merge.

Уровни:

```text
docs/ARCHITECTURE.md        stable semantic contract
docs/TECHNICAL_REFERENCE.md current implementation reference
docs/wiki/                  explanatory long-form documentation
docs/ru/                    maintained Russian translation layer
```

Английский слой остаётся canonical. Если изменяется английская page, соответствующий русский перевод обновляется в том же PR либо CI намеренно блокирует неполную структуру перевода.

## Local testing

Focused test:

```bat
.\gradlew.bat :simulation:test --tests "*RampNavigationHardeningTest" --rerun-tasks --console=plain
```

Перед merge:

```bat
.\gradlew.bat :simulation:test --rerun-tasks --console=plain
```

Routine `clean` не используется без причины.

## External review

External review нужен для challenge assumptions, а не automatic scope expansion. Finding становится actionable, когда указывает concrete violated rule или failing scenario.

```text
review claim
    ↓
smallest reproducing test
    ↓
actual failure confirmed?
    ├── no  -> document why
    └── yes -> minimal correction
```

## Deferred work записывается, а не строится наполовину

Реальный concern, отложенный намеренно, фиксируется в architecture/technical docs вместо partial infrastructure.

Например:

```text
geometry override lifecycle
navigation cache policy
path cost model
falling ownership
chunk/world-generation semantics
actor capabilities
```

Deferred decision должен оставлять public boundary пригодной для будущего решения.

## Изменения Wiki и переводов

English guide pages создаются в `docs/wiki/`, русские counterparts — в `docs/ru/wiki/`. VitePress публикует обе локали и даёт переключатель языка. GitHub Wiki workflow публикует английские pages под обычными names и русские под `RU-...`, потому что native GitHub Wiki не имеет locale routing.

Docs build проверяет links и структурную полноту locale pairs до merge.
