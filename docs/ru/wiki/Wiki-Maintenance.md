# Сопровождение документации

EvoForge использует documentation-as-code. Авторитетные sources документации находятся в основном репозитории и проходят тот же branch, pull-request, CI и review process, что и production code.

## Уровни документации и приоритет

### `docs/ARCHITECTURE.md`

**Нормативный архитектурный контракт.** FIXED ownership rules, boundaries, status markers, invariants и явно deferred decisions.

Если другой document противоречит `ARCHITECTURE.md` по FIXED rule, приоритет у `ARCHITECTURE.md`.

### `docs/TECHNICAL_REFERENCE.md`

**Справочник текущей реализации.** Package/class lists, current algorithms, concrete test coverage, implementation notes и known gaps.

Он может меняться вместе с реализацией без изменения stable architecture contract.

### `docs/wiki/`

**Объяснительное руководство.** Diagrams, examples, rationale, walkthroughs, extension recipes, terminology и development guidance.

```text
ARCHITECTURE.md        normative contract
        ↓
TECHNICAL_REFERENCE.md current implementation
        ↓
docs/wiki/             explanation and how-to
```

Стрелки обозначают authority, а не необходимость дублировать текст.

## Языки и канонический источник

Английская документация в `docs/` — канонический source semantics. Русский слой в `docs/ru/` — поддерживаемый перевод для чтения.

```text
docs/
├── ARCHITECTURE.md
├── TECHNICAL_REFERENCE.md
├── wiki/*.md
└── ru/
    ├── ARCHITECTURE.md
    ├── TECHNICAL_REFERENCE.md
    └── wiki/*.md
```

Имена paired pages сохраняются одинаковыми внутри locale directories. Это позволяет VitePress переключать locale предсказуемо и делает structural parity проверяемой CI.

Если перевод расходится с English source, English source имеет приоритет до исправления перевода. Semantic change в English docs должен обновлять affected Russian counterpart в том же PR.

## Один source, несколько publication targets

Markdown files — source. Publication systems — derived views.

```text
main repository docs/
        ├── GitHub Wiki mirror (English + RU-* pages)
        └── VitePress static site (English + /ru/ locale)
```

VitePress даёт native language selector, navigation, local full-text search, dark mode, edit links и last-updated.

GitHub Wiki не имеет native locale routing. Поэтому английские pages сохраняют обычные names, а русский publisher создаёт pages с префиксом `RU-`. Sidebar содержит language switch.

Ни один publisher не authoritative. При расхождении source Markdown в `main` побеждает.

## Почему Wiki source живёт в основном репозитории

GitHub хранит Wiki отдельным Git repository. Direct edit удобен, но обходит обычный PR review.

Flow:

```text
edit docs/wiki/*.md and docs/ru/wiki/*.md
    ↓
feature branch
    ↓
pull request + CI + documentation build
    ↓
merge to main
    ├── Sync Wiki workflow
    └── Docs Site workflow
```

## VitePress i18n

VitePress использует встроенную locale model:

```text
/       English
/ru/    Русский
```

В navbar отображается штатный locale switch. English routes сохраняются, поэтому существующие links не ломаются.

Locale-specific nav/sidebar/text настраиваются в `docs/.vitepress/config.mts`.

## Проверка полноты переводов

CI выполняет structural i18n check до VitePress build. Он проверяет, что для канонических core docs и каждой English guide page существует русский counterpart с тем же filename.

Это не может доказать литературное качество или semantic freshness перевода, но предотвращает тихое появление новой English page без русской пары.

VitePress build дополнительно проверяет обе локали и internal links.

## GitHub Wiki synchronization

Workflow `.github/workflows/sync-wiki.yml` создаёт staging tree:

- English pages публикуются с обычными names;
- Russian pages — как `RU-<Page>.md`;
- local links внутри русских pages переписываются на `RU-...` только в generated Wiki copy;
- `_Sidebar.md` объединяет English и Russian navigation;
- source files не изменяются.

Так один RU source одновременно корректно работает в VitePress `/ru/` и native GitHub Wiki.

## Static documentation site

VitePress настроен в `docs/.vitepress/`. Repository-level `package.json` содержит только documentation build tooling; Node.js не становится runtime dependency симуляции или Java build.

Команды:

```text
npm install
npm run docs:dev
npm run docs:build
npm run docs:preview
```

`docs:build` — validation step: unresolved links/generator errors ломают build.

`.github/workflows/docs-site.yml` строит site на PR и публикует в GitHub Pages после merge в `main`.

## Workflow permissions

Wiki workflow получает только permission, необходимый для push generated Wiki content. Static site build использует read-only repository access, deploy job — минимальные Pages permissions.

## Page naming и links

English guide files используют стабильные filename-safe names:

```text
Project-Overview.md
Architecture-Principles.md
Shape-Contract.md
```

Russian source использует те же filenames под `docs/ru/wiki/`. `RU-` существует только в generated GitHub Wiki.

В source используйте normal relative Markdown links:

```markdown
[Контракт Shape](Shape-Contract.md)
```

Не вставляйте `RU-` в source links: publisher добавляет его только для GitHub Wiki mirror.

## Обновление документации вместе с кодом

Если PR меняет semantic architecture rule:

```text
production change
+ tests
+ ARCHITECTURE.md if stable contract changed
+ TECHNICAL_REFERENCE.md if implementation changed
+ affected English guide pages
+ corresponding Russian translations
```

Internal implementation change может не требовать Wiki update, если guide не описывает implementation-specific detail.

## Review документации

Проверять:

```text
Is the ownership statement correct?
Is the example consistent with production behavior?
Does CURRENT accidentally become FIXED?
Does implementation masquerade as persistence identity?
Does a future subsystem appear before it exists?
Are diagrams/terms consistent?
Are English/Russian counterparts present?
Does the static site build without broken links?
```

## Publication verification

Для Wiki проверяются Home, combined Sidebar, English/Russian page switching, internal links и code blocks.

Для VitePress PR build — основная validation; после deploy проверяются landing page, locale selector и хотя бы один cross-locale route.

Если publisher падает, source docs сохраняются; исправляется pipeline, если failure не выявляет реальный source-document defect.
