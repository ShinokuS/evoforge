import { createHash } from 'node:crypto'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'

const root = resolve(process.cwd(), 'docs')
const englishWiki = join(root, 'wiki')
const russianRoot = join(root, 'ru')
const russianWiki = join(russianRoot, 'wiki')
const manifestPath = join(russianRoot, '.source-blobs.json')

const corePairs = [
  'index.md',
  'ARCHITECTURE.md',
  'TECHNICAL_REFERENCE.md'
]

function markdownFiles(directory) {
  return readdirSync(directory)
    .filter((name) => name.endsWith('.md'))
    .sort()
}

function gitBlobSha(content) {
  const body = Buffer.from(content, 'utf8')
  const header = Buffer.from(`blob ${body.length}\0`, 'utf8')
  return createHash('sha1').update(header).update(body).digest('hex')
}

const pairs = [
  ...corePairs.map((name) => ({
    source: join(root, name),
    translation: join(russianRoot, name),
    key: name
  })),
  ...markdownFiles(englishWiki).map((name) => ({
    source: join(englishWiki, name),
    translation: join(russianWiki, name),
    key: `wiki/${name}`
  }))
]

const errors = []

if (!existsSync(manifestPath)) {
  errors.push(`missing translation source manifest: ${relative(process.cwd(), manifestPath)}`)
}

let manifest = {}
if (existsSync(manifestPath)) {
  manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
}

for (const pair of pairs) {
  if (!existsSync(pair.translation)) {
    errors.push(`missing Russian counterpart for ${pair.key}`)
    continue
  }

  const source = readFileSync(pair.source, 'utf8')
  const translation = readFileSync(pair.translation, 'utf8')

  if (translation.trim().length < 20) {
    errors.push(`Russian counterpart is unexpectedly empty: ${pair.key}`)
  }

  if (source === translation) {
    errors.push(`Russian counterpart is identical to English source: ${pair.key}`)
  }

  const actualSha = gitBlobSha(source)
  const reviewedSha = manifest[pair.key]

  if (!reviewedSha) {
    errors.push(`missing reviewed source SHA for ${pair.key}`)
  } else if (reviewedSha !== actualSha) {
    errors.push(
      `English source changed since Russian translation review: ${pair.key} ` +
      `(expected ${reviewedSha}, actual ${actualSha})`
    )
  }
}

const expectedRussianWiki = new Set(markdownFiles(englishWiki))
for (const name of markdownFiles(russianWiki)) {
  if (!expectedRussianWiki.has(name)) {
    errors.push(`orphan Russian guide page without English source: wiki/${name}`)
  }
}

const expectedKeys = new Set(pairs.map((pair) => pair.key))
for (const key of Object.keys(manifest)) {
  if (!expectedKeys.has(key)) {
    errors.push(`stale manifest entry without canonical source: ${key}`)
  }
}

if (errors.length > 0) {
  console.error('Documentation i18n check failed:')
  for (const error of errors) {
    console.error(` - ${error}`)
  }
  process.exit(1)
}

console.log(`Documentation i18n check passed for ${pairs.length} English/Russian pairs.`)
