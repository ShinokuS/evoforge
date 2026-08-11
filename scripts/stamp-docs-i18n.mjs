import { createHash } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { isAbsolute, join, normalize, relative, resolve, sep } from 'node:path'
import { pathToFileURL } from 'node:url'

export function gitBlobSha(content) {
  const body = Buffer.from(content, 'utf8')
  const header = Buffer.from(`blob ${body.length}\0`, 'utf8')
  return createHash('sha1').update(header).update(body).digest('hex')
}

function normalizeKey(rawKey) {
  if (typeof rawKey !== 'string' || rawKey.trim().length === 0) {
    throw new Error('documentation key must be a non-empty string')
  }

  const slashKey = rawKey.trim().replaceAll('\\', '/')
  if (isAbsolute(slashKey) || slashKey.startsWith('docs/') || slashKey.startsWith('ru/')) {
    throw new Error(`use a canonical docs-relative key such as wiki/Movement-System.md: ${rawKey}`)
  }

  const normalized = normalize(slashKey).split(sep).join('/')
  if (normalized === '..' || normalized.startsWith('../') || !normalized.endsWith('.md')) {
    throw new Error(`invalid documentation key: ${rawKey}`)
  }

  return normalized
}

function assertInside(root, candidate, label) {
  const rel = relative(root, candidate)
  if (rel === '..' || rel.startsWith(`..${sep}`) || isAbsolute(rel)) {
    throw new Error(`${label} escapes documentation root`)
  }
}

export function stampDocsI18n({ docsRoot, keys }) {
  const root = resolve(docsRoot)
  const russianRoot = join(root, 'ru')
  const manifestPath = join(russianRoot, '.source-blobs.json')

  if (!Array.isArray(keys) || keys.length === 0) {
    throw new Error('no documentation keys supplied')
  }
  if (!existsSync(manifestPath)) {
    throw new Error(`missing translation source manifest: ${manifestPath}`)
  }

  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
  if (manifest === null || Array.isArray(manifest) || typeof manifest !== 'object') {
    throw new Error('translation source manifest must contain a JSON object')
  }

  const uniqueKeys = [...new Set(keys.map(normalizeKey))]
  const updates = []

  for (const key of uniqueKeys) {
    const sourcePath = resolve(root, key)
    const translationPath = resolve(russianRoot, key)
    assertInside(root, sourcePath, 'English source')
    assertInside(russianRoot, translationPath, 'Russian counterpart')

    if (!existsSync(sourcePath)) {
      throw new Error(`English source does not exist: ${key}`)
    }
    if (!existsSync(translationPath)) {
      throw new Error(`Russian counterpart does not exist: ${key}`)
    }

    const source = readFileSync(sourcePath, 'utf8')
    const translation = readFileSync(translationPath, 'utf8')

    if (translation.trim().length < 20) {
      throw new Error(`Russian counterpart is unexpectedly empty: ${key}`)
    }
    if (source === translation) {
      throw new Error(`Russian counterpart is identical to English source: ${key}`)
    }

    const sha = gitBlobSha(source)
    manifest[key] = sha
    updates.push({ key, sha })
  }

  writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
  return updates
}

export function main(argv = process.argv.slice(2), cwd = process.cwd()) {
  if (argv.length === 0) {
    throw new Error(
      'usage: npm run docs:i18n:stamp -- <docs-relative-key> [more keys]\n' +
      'example: npm run docs:i18n:stamp -- wiki/Movement-System.md'
    )
  }

  const updates = stampDocsI18n({
    docsRoot: join(cwd, 'docs'),
    keys: argv
  })

  for (const { key, sha } of updates) {
    console.log(`Stamped ${key} -> ${sha}`)
  }
}

const entry = process.argv[1] ? pathToFileURL(resolve(process.argv[1])).href : null
if (entry === import.meta.url) {
  try {
    main()
  } catch (error) {
    console.error(`Documentation i18n stamping failed: ${error.message}`)
    process.exit(1)
  }
}
