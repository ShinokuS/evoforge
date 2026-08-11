# Documentation Maintenance

EvoForge uses documentation-as-code. Authoritative documentation sources live in the main repository and move through the same branch, pull-request, CI, and review process as production code.

## Documentation layers and authority

The three documentation layers have different responsibilities and a clear precedence when wording overlaps.

### `docs/ARCHITECTURE.md`

**Normative architecture contract.** Put fixed ownership rules, architectural boundaries, status markers, invariants, and explicitly deferred decisions here.

If another document contradicts `ARCHITECTURE.md` on a FIXED architectural rule, `ARCHITECTURE.md` wins and the other document is stale.

### `docs/TECHNICAL_REFERENCE.md`

**Current implementation reference.** Put package/class lists, current algorithms, concrete test coverage, implementation notes, and known technical gaps here.

It may change as ordinary implementation evolves without changing the stable architecture contract.

### `docs/wiki/`

**Explanatory guide.** Put diagrams, examples, rationale, subsystem walkthroughs, extension recipes, terminology, development guidance, and cross-links here.

A guide page may explain a fixed contract in much more detail, but it must not silently redefine or contradict the architecture contract.

```text
ARCHITECTURE.md        normative contract
        ↓
TECHNICAL_REFERENCE.md current implementation
        ↓
docs/wiki/             explanation and how-to
```

The arrows mean authority, not a requirement to duplicate the same text three times.

## Languages and canonical semantics

English documentation under `docs/` is the canonical semantic source. Russian documentation under `docs/ru/` is a maintained translation intended for reading and discussion.

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

Paired guide pages intentionally use the same filename inside their locale directories. If English and Russian wording disagree, the English source remains authoritative until the translation is corrected.

A semantic change to an English document should update the affected Russian counterpart in the same pull request.

## Translation freshness guard

Structural parity alone is not enough: a Russian file could exist while silently describing an older architecture.

EvoForge therefore keeps `docs/ru/.source-blobs.json`. For every canonical English document it records the Git blob SHA that was explicitly reviewed when the Russian translation was last synchronized.

The documentation CI check verifies:

```text
English page exists
Russian page with the same source filename exists
translation is non-empty and not identical to English
recorded source blob == current English source blob
no orphan Russian guide pages exist
```

Changing an English source without reviewing its Russian counterpart therefore fails documentation CI. Updating the manifest is an explicit statement that the translation was checked against that exact English source version.

The manifest is a freshness guard, not machine translation. Human/agent review is still responsible for semantic quality.

## Explicit translation source stamping

Do not edit Git blob SHAs in `docs/ru/.source-blobs.json` by hand.

After the Russian counterpart has actually been reviewed against the changed English source, stamp only the reviewed documents explicitly:

```text
npm run docs:i18n:stamp -- wiki/Movement-System.md

npm run docs:i18n:stamp -- \
  wiki/Movement-System.md \
  wiki/Adding-a-Mechanic.md \
  wiki/Glossary.md
```

Keys are relative to the canonical `docs/` root and intentionally match manifest keys. The stamper:

```text
requires an explicit list of keys
requires both English and Russian files to exist
rejects empty or identical Russian counterparts
computes the real Git blob SHA of each English source
updates only the explicitly selected manifest entries
leaves all unrelated freshness markers unchanged
```

This is deliberately **not** an automatic “stamp everything changed” command. A recorded SHA is a review acknowledgement, so broad automatic stamping would defeat the freshness guard.

Recommended flow:

```text
edit canonical English page
        ↓
review/update Russian counterpart
        ↓
npm run docs:i18n:stamp -- <explicit reviewed keys>
        ↓
npm run docs:i18n:check
        ↓
PR / CI
```

The stamping helper itself is covered by Node's built-in test runner through `npm run docs:i18n:test`.

## One source, multiple publication targets

Markdown files under `docs/` are the source. Publication systems are derived views.

EvoForge publishes documentation in two ways:

```text
main repository docs/
        ├── GitHub Wiki mirror (English + Russian)
        └── VitePress static site (English + /ru/ locale)
```

Neither publication target is authoritative. If a generated view disagrees with source Markdown in `main`, source Markdown wins.

## VitePress internationalization

VitePress uses its built-in locale model:

```text
/       English
/ru/    Русский
```

The language selector is generated by VitePress from `locales` in `docs/.vitepress/config.mts`. English routes remain unchanged, so existing links keep working. Russian pages receive localized navigation, sidebar labels, edit-link text, and last-updated text.

The same Markdown filenames are used in both language trees, which keeps paired routes predictable:

```text
/wiki/Navigation
/ru/wiki/Navigation
```

## Why GitHub Wiki needs a different publication shape

GitHub Wiki is a separate Git repository and does not provide VitePress-style locale routing. Wiki page filenames map directly to page names, so a nested `/ru/` page namespace is not available as a normal locale tree.

The source remains clean and symmetric, while the generated Wiki uses:

```text
Navigation.md       English
RU-Navigation.md    Русский
```

This `RU-` prefix exists only in the generated Wiki repository. It must not be added to source filenames or source links.

Every generated ordinary Wiki page receives a language banner linking to its counterpart. The generated `_Sidebar.md` contains both English and Russian navigation.

## GitHub Wiki staging

The publisher does not copy `docs/wiki/` directly anymore. Instead:

```text
docs/wiki/*.md
        +
docs/ru/wiki/*.md
        ↓
scripts/prepare-wiki.mjs
        ↓
multilingual staging tree
        ↓
GitHub Wiki repository
```

For the Russian generated copy, normal relative guide links such as:

```markdown
[Navigation](Navigation.md)
```

become:

```markdown
[Navigation](RU-Navigation.md)
```

Only the generated copy is rewritten. The source Markdown stays portable and works normally under VitePress `/ru/`.

## Source directories

Normative and reference documents live directly under:

```text
docs/ARCHITECTURE.md
docs/TECHNICAL_REFERENCE.md
```

Long-form English guide pages live under:

```text
docs/wiki/
```

Russian counterparts live under:

```text
docs/ru/ARCHITECTURE.md
docs/ru/TECHNICAL_REFERENCE.md
docs/ru/wiki/
```

Special `_Sidebar.md` and `_Footer.md` files exist in both guide source trees. They are excluded from VitePress pages and consumed by the Wiki publication tooling.

The static-site landing pages and generator configuration live at:

```text
docs/index.md
docs/ru/index.md
docs/.vitepress/
```

## Static documentation validation

The VitePress workflow is `.github/workflows/docs-site.yml`.

For documentation pull requests it performs, in order:

```text
npm ci
i18n stamping-tool tests
translation parity/freshness check
multilingual Wiki staging validation
VitePress build of both locales
```

The build validates internal links and generator compatibility. Pull requests do not deploy. Merges to `main` build and publish to GitHub Pages.

Useful local commands remain:

```text
npm install
npm run docs:dev
npm run docs:build
npm run docs:preview
npm run docs:i18n:test
npm run docs:i18n:check
npm run docs:i18n:stamp -- wiki/Movement-System.md
node scripts/prepare-wiki.mjs wiki-stage
```

Node.js is documentation build tooling only; it is not a runtime dependency of the Java simulation.

## GitHub Wiki synchronization

The publishing workflow is `.github/workflows/sync-wiki.yml`.

It runs after relevant English/Russian Wiki source or generator changes reach `main`, and it can also be started manually. The workflow:

1. checks out the main repository;
2. generates the multilingual staging tree;
3. clones `${repository}.wiki.git` using `GITHUB_TOKEN`;
4. replaces the generated Wiki working tree;
5. commits only if content changed;
6. pushes the Wiki repository.

Direct edits to generated Wiki pages are therefore temporary. The main repository wins on the next synchronization.

## Workflow permissions

The Wiki workflow declares only the repository permission needed to push generated Wiki content. The static site build uses read-only repository access; its deploy job receives only the GitHub Pages permissions required by the Pages deployment flow.

Prefer explicit per-workflow or per-job permissions over broad repository-wide write permissions.

## Page naming and internal links

Use descriptive filename-safe English source names and preserve them across locales:

```text
Project-Overview.md
Architecture-Principles.md
Shape-Contract.md
Roadmap-and-Deferred-Decisions.md
```

Inside guide source use normal relative Markdown links:

```markdown
[Shape Contract](Shape-Contract.md)
```

Do not write `RU-` into Russian source links. The Wiki staging generator adds it only to the generated mirror.

Avoid casual source page renames because both published routes and external links may depend on current slugs.

## Updating documentation with code

When a pull request changes a semantic architecture rule:

```text
production change
+ tests
+ ARCHITECTURE.md if the stable contract changed
+ TECHNICAL_REFERENCE.md if implementation changed
+ affected English guide pages
+ corresponding Russian translations
+ explicit translation source stamping for reviewed pages
```

When a pull request changes only internal implementation, the guide may need no update unless it contains implementation-specific material.

When a pull request only improves explanation/navigation, do not change the normative contract just to align timestamps.

## Avoiding stale generated claims

Do not copy fragile statistics such as exact class counts or line numbers into many pages. Prefer stable descriptions and links to the relevant subsystem page.

When a concrete number is semantically important, explain why it exists. Navigation's current Z read range, for example, is documented as a derivation from Shape roles rather than merely as “the loop scans four layers.”

## Reviewing documentation changes

Review documentation with the same questions used for code:

```text
Is the ownership statement correct?
Is the example consistent with production behavior?
Does the page accidentally turn a CURRENT convention into a FIXED rule?
Does it describe implementation as if it were persistence identity?
Does it introduce a future subsystem that does not exist?
Are diagrams and terminology consistent between locales?
Was every affected translation reviewed against the new English source?
Were only explicitly reviewed pages stamped?
Does the static site build without broken internal links?
Does multilingual Wiki staging produce both language graphs?
```

## Publication verification

For Wiki changes, verify English `Home`, Russian `RU-Home`, the combined `_Sidebar`, language counterpart links, internal links, and code blocks.

For VitePress, the pull-request build is primary validation. After a deployment change reaches `main`, verify the landing page, locale selector, `/ru/`, and at least one paired guide route.

If a publisher fails, keep source documents intact and fix the pipeline unless the failure exposes a real source-document defect.
