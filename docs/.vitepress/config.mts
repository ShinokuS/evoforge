import { readdirSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, type DefaultTheme } from 'vitepress'

const docsRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')

function titleFor(file: string): string {
  const source = readFileSync(file, 'utf8')
  const heading = source.match(/^#\s+(.+)$/m)?.[1]
  return heading ?? file.split('/').at(-1)!.replace(/\.md$/, '').replace(/-/g, ' ')
}

function sectionItems(
  directory: string,
  options: { reverse?: boolean } = {}
): DefaultTheme.SidebarItem[] {
  const root = resolve(docsRoot, directory)
  const names = readdirSync(root)
    .filter((name) => name.endsWith('.md') && name !== 'index.md' && !name.startsWith('_'))
    .sort((a, b) => a.localeCompare(b))

  if (options.reverse) names.reverse()

  return names.map((name) => ({
    text: titleFor(resolve(root, name)),
    link: `/${directory}/${name.replace(/\.md$/, '')}`
  }))
}

function group(
  text: string,
  directory: string,
  options: { reverse?: boolean } = {}
): DefaultTheme.SidebarItem {
  return {
    text,
    collapsed: false,
    items: sectionItems(directory, options)
  }
}

const sidebar: DefaultTheme.SidebarItem[] = [
  {
    text: 'Project',
    items: [
      { text: 'Project Context', link: '/project-context' },
      { text: 'Architecture', link: '/architecture' },
      { text: 'Roadmap', link: '/roadmap' },
      { text: 'References', link: '/references' },
      { text: 'Development Workflow', link: '/guides/development-workflow' }
    ]
  },
  {
    text: 'Systems',
    items: [
      { text: 'Systems Overview', link: '/systems/' },
      group('Foundations', 'systems/foundations'),
      group('Movement & Navigation', 'systems/traversal'),
      group('Agents & Life', 'systems/agents'),
      group('Environment', 'systems/environment'),
      group('World Generation', 'systems/world-generation'),
      group('Tools & Diagnostics', 'systems/tooling')
    ]
  },
  {
    text: 'Decisions',
    items: [
      { text: 'Decision Registry', link: '/decisions/' },
      ...sectionItems('decisions')
    ]
  },
  { text: 'Guides', items: sectionItems('guides') },
  {
    text: 'Development Journal',
    items: [
      { text: 'About the Journal', link: '/journal/' },
      group('Recent Entries', 'journal/entries', { reverse: true }),
      group('Design Explorations', 'journal/design'),
      group('Acceptance Records', 'journal/acceptance'),
      group('Audits', 'journal/audits', { reverse: true })
    ]
  }
]

export default defineConfig({
  title: 'EvoForge',
  description: 'Human-readable architecture, exact system models, decisions and development history for EvoForge.',
  base: '/evoforge/',
  cleanUrls: true,
  lastUpdated: true,

  themeConfig: {
    nav: [
      { text: 'Context', link: '/project-context' },
      { text: 'Systems', link: '/systems/' },
      { text: 'Roadmap', link: '/roadmap' },
      { text: 'Decisions', link: '/decisions/' },
      { text: 'Journal', link: '/journal/' },
      { text: 'GitHub', link: 'https://github.com/ShinokuS/evoforge' }
    ],
    sidebar,
    search: { provider: 'local' },
    outline: { level: [2, 3] },
    editLink: {
      pattern: 'https://github.com/ShinokuS/evoforge/edit/main/docs/:path',
      text: 'Edit this page on GitHub'
    },
    lastUpdated: { text: 'Last updated' },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/ShinokuS/evoforge' }
    ]
  }
})
