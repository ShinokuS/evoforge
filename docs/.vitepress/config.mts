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
    .filter((name) => name.endsWith('.md') && name !== 'index.md')
    .sort((a, b) => a.localeCompare(b))

  if (options.reverse) names.reverse()

  return names.map((name) => ({
    text: titleFor(resolve(root, name)),
    link: `/${directory}/${name.replace(/\.md$/, '')}`
  }))
}

const sidebar: DefaultTheme.SidebarItem[] = [
  {
    text: 'Project',
    items: [
      { text: 'Architecture', link: '/architecture' },
      { text: 'Roadmap', link: '/roadmap' },
      { text: 'Development Workflow', link: '/guides/development-workflow' }
    ]
  },
  { text: 'Systems', items: sectionItems('systems') },
  { text: 'Decisions', items: sectionItems('decisions') },
  { text: 'Guides', items: sectionItems('guides') },
  {
    text: 'Development Journal',
    items: [
      { text: 'About the Journal', link: '/notes/' },
      ...sectionItems('notes', { reverse: true })
    ]
  }
]

export default defineConfig({
  title: 'EvoForge',
  description: 'Architecture, system contracts and development journal for EvoForge.',
  base: '/evoforge/',
  cleanUrls: true,
  lastUpdated: true,

  themeConfig: {
    nav: [
      { text: 'Architecture', link: '/architecture' },
      { text: 'Roadmap', link: '/roadmap' },
      { text: 'Workflow', link: '/guides/development-workflow' },
      { text: 'Systems', link: '/systems/runtime' },
      { text: 'Journal', link: '/notes/' },
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
