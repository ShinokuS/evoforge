# Wiki Maintenance

The GitHub Wiki is a generated publication target. The authoritative source for Wiki content is `docs/wiki/` in the main EvoForge repository.

## Why the source lives in the main repository

GitHub stores a Wiki as a separate Git repository. Editing it directly is convenient, but it bypasses the normal source-code pull-request review path and makes architecture changes easier to separate accidentally from their documentation.

EvoForge therefore uses this flow:

```text
edit docs/wiki/*.md
    ↓
normal feature branch
    ↓
pull request review
    ↓
merge to main
    ↓
GitHub Actions
    ↓
EvoForge Wiki repository
```

This keeps documentation versioned and reviewed alongside code.

## Source directory

All published Wiki files live under:

```text
docs/wiki/
```

Pages are intentionally kept as flat Markdown files because GitHub Wiki page names map naturally to filenames.

Special GitHub Wiki files are also stored there:

```text
_Sidebar.md
_Footer.md
```

## Synchronization workflow

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

## Permissions

The workflow declares only:

```yaml
permissions:
  contents: write
```

This is preferable to granting every workflow broad write permissions by default. Repository or organization policy may still restrict the effective `GITHUB_TOKEN`; if GitHub denies the push, check the repository Actions permissions.

## First-time Wiki initialization

GitHub creates the clonable Wiki Git repository after the Wiki has been initialized with a page. If the sync workflow reports that the Wiki repository cannot be cloned, create a first page once from the GitHub Wiki UI and rerun the workflow.

After that bootstrap, `docs/wiki/` becomes the source of truth.

## Page naming

Use descriptive filename-safe names:

```text
Project-Overview.md
Architecture-Principles.md
Shape-Contract.md
Roadmap-and-Deferred-Decisions.md
```

Avoid renaming pages casually because existing external links may point to their current Wiki slugs.

## Internal links

Use normal relative Markdown links so links work both while reviewing `docs/wiki/` in the repository and after publication:

```markdown
[Shape Contract](Shape-Contract.md)
```

Prefer linking to a page rather than duplicating the same explanation in many places.

## Documentation responsibilities

The three documentation layers have different responsibilities.

### `ARCHITECTURE.md`

Short, normative, and stable. Put fixed ownership rules, architectural boundaries, status markers, and explicitly deferred decisions here.

### `TECHNICAL_REFERENCE.md`

Implementation-oriented and current. Put package/class lists, current algorithms, concrete test coverage, implementation notes, and known technical gaps here.

### Wiki

Explanatory and educational. Put diagrams, examples, rationale, subsystem walkthroughs, extension recipes, terminology, and cross-links here.

A Wiki page may explain a fixed contract in much more detail, but it must not quietly contradict `ARCHITECTURE.md`.

## Updating documentation with code

When a PR changes a semantic architecture rule:

```text
production change
+ tests
+ ARCHITECTURE.md if the stable contract changed
+ TECHNICAL_REFERENCE.md if implementation changed
+ affected Wiki pages
```

When a PR only changes an internal implementation without changing semantics, the Wiki may need no update unless it contains implementation-specific material.

## Avoiding stale generated claims

Do not copy fragile statistics such as exact class counts or line numbers into many pages. Prefer stable descriptions and direct links to the relevant subsystem page.

When a concrete number is semantically important, explain why it exists. For example, Navigation's current Z read range is documented as a derivation from Shape roles, not merely as “the loop scans four layers.”

## Reviewing Wiki changes

Review documentation with the same questions used for code:

```text
Is the ownership statement correct?
Is the example consistent with production behavior?
Does the page accidentally turn a CURRENT convention into a FIXED rule?
Does it describe implementation as if it were persistence identity?
Does it introduce a future subsystem that does not exist?
Are diagrams and terminology consistent with other pages?
```

## Manual publication test

After the synchronization PR is merged, run the `Sync Wiki` workflow manually once if necessary. Verify that `Home`, `_Sidebar`, internal links, and code blocks render correctly in the GitHub Wiki.

If authentication fails, keep `docs/wiki/` intact and fix only the publishing workflow; documentation content should never depend on Wiki availability.
