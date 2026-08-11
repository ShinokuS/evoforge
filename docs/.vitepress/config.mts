import { defineConfig } from 'vitepress'

const englishSidebar = [
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
      { text: 'Control Backbone', link: '/wiki/Control-Backbone' },
      { text: 'Spatial System', link: '/wiki/Spatial-System' },
      { text: 'Landscape and Terrain', link: '/wiki/Landscape-and-Terrain' },
      { text: 'Geometry System', link: '/wiki/Geometry-System' },
      { text: 'Movement System', link: '/wiki/Movement-System' }
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

const russianSidebar = [
  {
    text: 'Основы',
    items: [
      { text: 'Главная', link: '/ru/wiki/Home' },
      { text: 'Обзор проекта', link: '/ru/wiki/Project-Overview' },
      { text: 'Архитектурные принципы', link: '/ru/wiki/Architecture-Principles' },
      { text: 'Структура проекта', link: '/ru/wiki/Project-Structure' },
      { text: 'Модель мира', link: '/ru/wiki/World-Model' },
      { text: 'Глоссарий', link: '/ru/wiki/Glossary' }
    ]
  },
  {
    text: 'Geometry и Navigation',
    items: [
      { text: 'Контракт Shape', link: '/ru/wiki/Shape-Contract' },
      { text: 'Алгебра переходов', link: '/ru/wiki/Transition-Algebra' },
      { text: 'Navigation', link: '/ru/wiki/Navigation' },
      { text: 'FullShape', link: '/ru/wiki/FullShape' },
      { text: 'RampShape', link: '/ru/wiki/RampShape' },
      { text: 'Добавление Shape', link: '/ru/wiki/Adding-a-Shape' }
    ]
  },
  {
    text: 'Системы',
    items: [
      { text: 'Definitions', link: '/ru/wiki/Definitions' },
      { text: 'Модель объектов', link: '/ru/wiki/Object-Model' },
      { text: 'Время и Scheduler', link: '/ru/wiki/Time-and-Scheduler' },
      { text: 'Control Backbone', link: '/ru/wiki/Control-Backbone' },
      { text: 'Spatial System', link: '/ru/wiki/Spatial-System' },
      { text: 'Landscape и Terrain', link: '/ru/wiki/Landscape-and-Terrain' },
      { text: 'Geometry System', link: '/ru/wiki/Geometry-System' },
      { text: 'Movement System', link: '/ru/wiki/Movement-System' }
    ]
  },
  {
    text: 'Разработка',
    items: [
      { text: 'Стратегия тестирования', link: '/ru/wiki/Testing-Strategy' },
      { text: 'Процесс разработки', link: '/ru/wiki/Development-Workflow' },
      { text: 'Добавление механики', link: '/ru/wiki/Adding-a-Mechanic' },
      { text: 'Дорожная карта и отложенные решения', link: '/ru/wiki/Roadmap-and-Deferred-Decisions' },
      { text: 'Сопровождение документации', link: '/ru/wiki/Wiki-Maintenance' }
    ]
  }
]

export default defineConfig({
  title: 'EvoForge',
  base: '/evoforge/',
  cleanUrls: true,
  lastUpdated: true,
  srcExclude: [
    'wiki/_Sidebar.md',
    'wiki/_Footer.md',
    'ru/wiki/_Sidebar.md',
    'ru/wiki/_Footer.md'
  ],

  locales: {
    root: {
      label: 'English',
      lang: 'en-US',
      description: 'Architecture, systems, and development documentation for EvoForge.',
      themeConfig: {
        nav: [
          { text: 'Guide', link: '/wiki/Home' },
          { text: 'Architecture', link: '/ARCHITECTURE' },
          { text: 'Technical Reference', link: '/TECHNICAL_REFERENCE' },
          { text: 'GitHub', link: 'https://github.com/ShinokuS/evoforge' }
        ],
        sidebar: {
          '/wiki/': englishSidebar
        },
        editLink: {
          pattern: 'https://github.com/ShinokuS/evoforge/edit/main/docs/:path',
          text: 'Edit this page on GitHub'
        },
        lastUpdated: {
          text: 'Last updated'
        }
      }
    },
    ru: {
      label: 'Русский',
      lang: 'ru-RU',
      link: '/ru/',
      description: 'Архитектура, системы и руководство по разработке EvoForge.',
      themeConfig: {
        nav: [
          { text: 'Руководство', link: '/ru/wiki/Home' },
          { text: 'Архитектура', link: '/ru/ARCHITECTURE' },
          { text: 'Технический справочник', link: '/ru/TECHNICAL_REFERENCE' },
          { text: 'GitHub', link: 'https://github.com/ShinokuS/evoforge' }
        ],
        sidebar: {
          '/ru/wiki/': russianSidebar
        },
        editLink: {
          pattern: 'https://github.com/ShinokuS/evoforge/edit/main/docs/:path',
          text: 'Редактировать эту страницу на GitHub'
        },
        lastUpdated: {
          text: 'Обновлено'
        }
      }
    }
  },

  themeConfig: {
    search: {
      provider: 'local'
    },
    outline: {
      level: [2, 3]
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/ShinokuS/evoforge' }
    ]
  }
})
