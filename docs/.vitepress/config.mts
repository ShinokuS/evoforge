import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'en-US',
  title: 'EvoForge',
  description: 'Architecture, systems, and development documentation for EvoForge.',
  base: '/evoforge/',
  cleanUrls: true,
  lastUpdated: true,
  srcExclude: [
    'wiki/_Sidebar.md',
    'wiki/_Footer.md'
  ],

  themeConfig: {
    nav: [
      { text: 'Guide', link: '/wiki/Home' },
      { text: 'Architecture', link: '/ARCHITECTURE' },
      { text: 'Technical Reference', link: '/TECHNICAL_REFERENCE' },
      { text: 'GitHub', link: 'https://github.com/ShinokuS/evoforge' }
    ],

    sidebar: {
      '/wiki/': [
        {
          text: 'Foundations',
          items: [
            { text: 'Home', link: '/wiki/Home' },
            { text: 'Project Overview', link: '/wiki/Project-Overview' },
            { text: 'Architecture Principles', link: '/wiki/Architecture-Principles' },
            { text: 'Project Structure', link: '/wiki/Project-Structure' },
            { text: 'World Model', link: '/wiki/World-Model' },
            { text: 'Glossary', link: '/wiki/Glossary' }
          ]
        },
        {
          text: 'Geometry & Navigation',
          items: [
            { text: 'Shape Contract', link: '/wiki/Shape-Contract' },
            { text: 'Transition Algebra', link: '/wiki/Transition-Algebra' },
            { text: 'Navigation', link: '/wiki/Navigation' },
            { text: 'FullShape', link: '/wiki/FullShape' },
            { text: 'RampShape', link: '/wiki/RampShape' },
            { text: 'Adding a Shape', link: '/wiki/Adding-a-Shape' }
          ]
        },
        {
          text: 'Systems',
          items: [
            { text: 'Definitions', link: '/wiki/Definitions' },
            { text: 'Object Model', link: '/wiki/Object-Model' },
            { text: 'Time and Scheduler', link: '/wiki/Time-and-Scheduler' },
            { text: 'Spatial System', link: '/wiki/Spatial-System' },
            { text: 'Landscape and Terrain', link: '/wiki/Landscape-and-Terrain' },
            { text: 'Geometry System', link: '/wiki/Geometry-System' }
          ]
        },
        {
          text: 'Development',
          items: [
            { text: 'Testing Strategy', link: '/wiki/Testing-Strategy' },
            { text: 'Development Workflow', link: '/wiki/Development-Workflow' },
            { text: 'Adding a Mechanic', link: '/wiki/Adding-a-Mechanic' },
            { text: 'Roadmap & Deferred Decisions', link: '/wiki/Roadmap-and-Deferred-Decisions' },
            { text: 'Documentation Maintenance', link: '/wiki/Wiki-Maintenance' }
          ]
        }
      ]
    },

    search: {
      provider: 'local'
    },

    editLink: {
      pattern: 'https://github.com/ShinokuS/evoforge/edit/main/docs/:path',
      text: 'Edit this page on GitHub'
    },

    lastUpdated: {
      text: 'Last updated'
    },

    outline: {
      level: [2, 3]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/ShinokuS/evoforge' }
    ]
  }
})
