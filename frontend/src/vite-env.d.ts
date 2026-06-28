declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module '../../package.json' {
  const pkg: {
    name: string
    version: string
    description: string
  }
  export default pkg
}
