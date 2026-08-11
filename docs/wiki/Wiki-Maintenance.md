# Documentation Maintenance

EvoForge uses documentation-as-code. The authoritative documentation sources live in the main repository and move through the same branch, pull-request, CI, and review process as production code.

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

## One source, multiple publication targets

The Markdown files under `docs/` are the source. Publication systems are derived views.

EvoForge currently publishes documentation in two ways:

```text
main repository docs/
        ├── GitHub Wiki mirror
        └── VitePress static site on GitHub Pages
```

The GitHub Wiki is convenient inside the repository UI. The VitePress site provides stronger reading ergonomics such as structured navigation, local full-text search, dark mode, edit links, and last-updated information.

Neither publication target is authoritative. If either generated view disagrees with the Markdown in `main`, the Markdown in `main` wins.

## Why Wiki source lives in the main repository

GitHub stores a Wiki as a separate Git repository. Editing it directly is convenient, but it bypasses the normal source-code pull-request review path and makes architecture changes easier to separate accidentally from their documentation.

EvoForge therefore uses this flow:

```text
edit docs/wiki/*.md
    ↓
normal feature branch
    ↓
pull request review + documentation build
    ↓
merge to main
    ├── Sync Wiki workflow
    └── Docs Site workflow
```

This keeps documentation versioned and reviewed alongside code.

## Source directories

Normative and reference documents live directly under:

```text
docs/ARCHITECTURE.md
docs/TECHNICAL_REFERENCE.md
```

Long-form guide pages live under:

```text
docs/wiki/
```

The guide pages remain flat Markdown files because their filenames map naturally to GitHub Wiki page names and also produce stable VitePress routes.

Special GitHub Wiki files are stored there too:

```text
_Sidebar.md
_Footer.md
```

They are used by the GitHub Wiki publisher and excluded from the VitePress page set.

The static site's landing page and generator configuration live at:

```text
docs/index.md
docs/.vitepress/
```

## GitHub Wiki synchronization

The publishing workflow is:

```text
.github/workflows/sync-wiki.yml
```

It runs when `docs/wiki/**` or the workflow itself changes on `main`, and it can also be started manually with `workflow_dispatch`.

The workflow:

1. checks out the main repository;
2. clones `${repository}.wiki.git` using the workflow `GITHUB_TOKEN`;
3. replaces the Wiki working tree with `docs/wiki/`;
4. creates a commit only when content changed;
5. pushes the Wiki repository.

The main repository therefore wins if someone edits the generated Wiki directly. The next sync will replace those direct edits.

## Static documentation site

The VitePress site is configured under:

```text
docs/.vitepress/
```

The repository-level `package.json` contains documentation-only Node.js tooling. This does not make Node.js part of the EvoForge simulation runtime or Java build; it is a build-time dependency for the documentation presentation layer only.

Useful local commands are:

```text
npm install
npm run docs:dev
npm run docs:build
npm run docs:preview
```

`docs:build` is also a documentation validation step: unresolved internal links and generator errors fail the build instead of silently publishing a broken site.

The deployment workflow is:

```text
.github/workflows/docs-site.yml
```

Pull requests that touch documentation build the site but do not deploy it. Changes merged to `main` build and publish the resulting static site to GitHub Pages.

## Workflow permissions

The Wiki workflow declares only the repository permission needed to push the generated Wiki content:

```yaml
permissions:
  contents: write
```

The static site build uses read-only repository access. Its deployment job receives only the GitHub Pages permissions required by the official Pages deployment flow.

Prefer explicit per-workflow or per-job permissions over broad repository-wide write permissions.

## First-time publication setup

### GitHub Wiki

GitHub creates the clonable Wiki Git repository after the Wiki has been initialized with a page. If the sync workflow reports that the Wiki repository cannot be cloned, create a first page once from the GitHub Wiki UI and rerun the workflow.

### GitHub Pages

GitHub Pages must be enabled once in repository settings with **GitHub Actions** selected as the publishing source. After that, merges to `main` publish through `docs-site.yml` automatically.

Publication availability must never determine whether documentation source can be reviewed or merged: the Markdown remains usable directly in the repository.

## Page naming

Use descriptive filename-safe names:

```text
Project-Overview.md
Architecture-Principles.md
Shape-Contract.md
Roadmap-and-Deferred-Decisions.md
```

Avoid renaming pages casually because external GitHub Wiki links and static-site routes may point to their current slugs.

## Internal links

Use normal relative Markdown links inside guide pages:

```markdown
[Shape Contract](Shape-Contract.md)
```

This keeps the source readable on GitHub and lets both GitHub Wiki and VitePress resolve the same content graph.

Prefer linking to a page rather than duplicating the same explanation in many places.

## Updating documentation with code

When a PR changes a semantic architecture rule:

```text
production change
+ tests
+ ARCHITECTURE.md if the stable contract changed
+ TECHNICAL_REFERENCE.md if implementation changed
+ affected guide pages
```

When a PR only changes an internal implementation without changing semantics, the guide may need no update unless it contains implementation-specific material.

When a PR only improves explanation or navigation, do not change the normative contract just to keep file timestamps aligned.

## Avoiding stale generated claims

Do not copy fragile statistics such as exact class counts or line numbers into many pages. Prefer stable descriptions and direct links to the relevant subsystem page.

When a concrete number is semantically important, explain why it exists. For example, Navigation's current Z read range is documented as a derivation from Shape roles, not merely as “the loop scans four layers.”

## Reviewing documentation changes

Review documentation with the same questions used for code:

```text
Is the ownership statement correct?
Is the example consistent with production behavior?
Does the page accidentally turn a CURRENT convention into a FIXED rule?
Does it describe implementation as if it were persistence identity?
Does it introduce a future subsystem that does not exist?
Are diagrams and terminology consistent with other pages?
Does the static site build without broken internal links?
```

## Publication verification

For Wiki changes, verify that `Home`, `_Sidebar`, internal links, and code blocks render correctly after synchronization.

For static-site changes, the pull-request build is the primary validation. After a deployment change reaches `main`, verify the Pages workflow and the published landing page once.

If either publisher fails, keep the source documents intact and fix only the publication pipeline unless the failure identifies a real source-document defect.
