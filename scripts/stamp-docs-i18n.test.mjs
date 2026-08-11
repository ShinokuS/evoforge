import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'

import { gitBlobSha, stampDocsI18n } from './stamp-docs-i18n.mjs'

function fixture() {
  const base = mkdtempSync(join(tmpdir(), 'evoforge-docs-i18n-'))
  const docsRoot = join(base, 'docs')
  mkdirSync(join(docsRoot, 'wiki'), { recursive: true })
  mkdirSync(join(docsRoot, 'ru', 'wiki'), { recursive: true })
  return { base, docsRoot }
}

function writePair(docsRoot, key, english, russian) {
  writeFileSync(join(docsRoot, key), english, 'utf8')
  writeFileSync(join(docsRoot, 'ru', key), russian, 'utf8')
}

function writeManifest(docsRoot, manifest) {
  writeFileSync(
    join(docsRoot, 'ru', '.source-blobs.json'),
    `${JSON.stringify(manifest, null, 2)}\n`,
    'utf8'
  )
}

function readManifest(docsRoot) {
  return JSON.parse(readFileSync(join(docsRoot, 'ru', '.source-blobs.json'), 'utf8'))
}

test('stamps only explicitly selected documents', () => {
  const { base, docsRoot } = fixture()
  try {
    writePair(docsRoot, 'wiki/A.md', '# A\nEnglish A\n', '# A RU\nРусский перевод страницы A.\n')
    writePair(docsRoot, 'wiki/B.md', '# B\nEnglish B\n', '# B RU\nРусский перевод страницы B.\n')
    writeManifest(docsRoot, {
      'wiki/A.md': 'old-a',
      'wiki/B.md': 'old-b'
    })

    const updates = stampDocsI18n({ docsRoot, keys: ['wiki/A.md'] })
    const manifest = readManifest(docsRoot)

    assert.deepEqual(updates, [
      { key: 'wiki/A.md', sha: gitBlobSha('# A\nEnglish A\n') }
    ])
    assert.equal(manifest['wiki/A.md'], gitBlobSha('# A\nEnglish A\n'))
    assert.equal(manifest['wiki/B.md'], 'old-b')
  } finally {
    rmSync(base, { recursive: true, force: true })
  }
})

test('adds a new manifest entry only when both locale files exist', () => {
  const { base, docsRoot } = fixture()
  try {
    writePair(
      docsRoot,
      'wiki/New.md',
      '# New\nCanonical source\n',
      '# New RU\nПроверенный русский перевод новой страницы.\n'
    )
    writeManifest(docsRoot, {})

    stampDocsI18n({ docsRoot, keys: ['wiki/New.md'] })

    assert.equal(
      readManifest(docsRoot)['wiki/New.md'],
      gitBlobSha('# New\nCanonical source\n')
    )
  } finally {
    rmSync(base, { recursive: true, force: true })
  }
})

test('rejects an unknown English source', () => {
  const { base, docsRoot } = fixture()
  try {
    writeManifest(docsRoot, {})
    assert.throws(
      () => stampDocsI18n({ docsRoot, keys: ['wiki/Missing.md'] }),
      /English source does not exist/
    )
  } finally {
    rmSync(base, { recursive: true, force: true })
  }
})

test('rejects a missing Russian counterpart', () => {
  const { base, docsRoot } = fixture()
  try {
    writeFileSync(join(docsRoot, 'wiki', 'Only-English.md'), '# English only\n', 'utf8')
    writeManifest(docsRoot, {})
    assert.throws(
      () => stampDocsI18n({ docsRoot, keys: ['wiki/Only-English.md'] }),
      /Russian counterpart does not exist/
    )
  } finally {
    rmSync(base, { recursive: true, force: true })
  }
})

test('rejects paths outside the canonical docs key space', () => {
  const { base, docsRoot } = fixture()
  try {
    writeManifest(docsRoot, {})
    assert.throws(
      () => stampDocsI18n({ docsRoot, keys: ['../README.md'] }),
      /invalid documentation key/
    )
    assert.throws(
      () => stampDocsI18n({ docsRoot, keys: ['docs/wiki/A.md'] }),
      /use a canonical docs-relative key/
    )
  } finally {
    rmSync(base, { recursive: true, force: true })
  }
})

test('rejects an identical translation before stamping', () => {
  const { base, docsRoot } = fixture()
  try {
    const content = '# Same\nThis should not count as a translation.\n'
    writePair(docsRoot, 'wiki/Same.md', content, content)
    writeManifest(docsRoot, {})

    assert.throws(
      () => stampDocsI18n({ docsRoot, keys: ['wiki/Same.md'] }),
      /identical to English source/
    )
  } finally {
    rmSync(base, { recursive: true, force: true })
  }
})
