import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { basename, join, resolve } from 'node:path'

const output = resolve(process.cwd(), process.argv[2] ?? 'wiki-stage')
const englishDir = resolve(process.cwd(), 'docs/wiki')
const russianDir = resolve(process.cwd(), 'docs/ru/wiki')

const specialFiles = new Set(['_Sidebar.md', '_Footer.md'])

function markdownFiles(directory) {
  return readdirSync(directory)
    .filter((name) => name.endsWith('.md'))
    .sort()
}

function pageName(filename) {
  return filename.replace(/\.md$/, '')
}

function russianWikiName(filename) {
  return `RU-${filename}`
}

function localizeRussianLinks(markdown) {
  return markdown.replace(/(!?\[[^\]]*\]\()([^)]+)(\))/g, (whole, open, target, close) => {
    const trimmed = target.trim()
    if (
      trimmed.startsWith('http://') ||
      trimmed.startsWith('https://') ||
      trimmed.startsWith('mailto:') ||
      trimmed.startsWith('/') ||
      trimmed.startsWith('#')
    ) {
      return whole
    }

    const match = trimmed.match(/^([^/#?]+\.md)([?#].*)?$/)
    if (!match) {
      return whole
    }

    const localized = `RU-${match[1]}${match[2] ?? ''}`
    return `${open}${localized}${close}`
  })
}

rmSync(output, { recursive: true, force: true })
mkdirSync(output, { recursive: true })

for (const filename of markdownFiles(englishDir)) {
  if (specialFiles.has(filename)) continue

  const source = readFileSync(join(englishDir, filename), 'utf8')
  const banner = `> **Language:** **English** · [Русский](${russianWikiName(filename)})\n\n`
  writeFileSync(join(output, filename), banner + source, 'utf8')
}

for (const filename of markdownFiles(russianDir)) {
  if (specialFiles.has(filename)) continue

  const source = readFileSync(join(russianDir, filename), 'utf8')
  const banner = `> **Язык:** [English](${filename}) · **Русский**\n\n`
  writeFileSync(
    join(output, russianWikiName(filename)),
    banner + localizeRussianLinks(source),
    'utf8'
  )
}

const englishSidebar = readFileSync(join(englishDir, '_Sidebar.md'), 'utf8').trim()
const russianSidebar = localizeRussianLinks(
  readFileSync(join(russianDir, '_Sidebar.md'), 'utf8').trim()
)

writeFileSync(
  join(output, '_Sidebar.md'),
  [
    '### Language / Язык',
    '[English](Home.md) · [Русский](RU-Home.md)',
    '',
    '---',
    '',
    '## English',
    englishSidebar,
    '',
    '---',
    '',
    '## Русский',
    russianSidebar,
    ''
  ].join('\n'),
  'utf8'
)

writeFileSync(
  join(output, '_Footer.md'),
  '---\nEvoForge documentation is generated from the main repository. · Документация EvoForge генерируется из основного репозитория.\n',
  'utf8'
)

for (const directory of [englishDir, russianDir]) {
  for (const name of readdirSync(directory)) {
    if (name.endsWith('.md')) continue
    const source = join(directory, name)
    const destinationName = directory === russianDir ? `RU-${basename(name)}` : basename(name)
    cpSync(source, join(output, destinationName), { recursive: true })
  }
}

const englishPages = markdownFiles(englishDir).filter((name) => !specialFiles.has(name)).length
const russianPages = markdownFiles(russianDir).filter((name) => !specialFiles.has(name)).length

if (englishPages !== russianPages) {
  throw new Error(`Wiki staging page count mismatch: English=${englishPages}, Russian=${russianPages}`)
}

if (!existsSync(join(output, 'Home.md')) || !existsSync(join(output, 'RU-Home.md'))) {
  throw new Error('Wiki staging is missing one of the language Home pages')
}

console.log(`Prepared multilingual GitHub Wiki: ${englishPages} English + ${russianPages} Russian pages.`)
