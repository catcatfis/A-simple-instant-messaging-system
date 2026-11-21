/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API 前缀 */
  readonly VITE_API_PREFIX: string
  /** WS 地址 */
  readonly VITE_WS_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/**
 * JSX 类型声明
 * <p>
 * 项目使用 @vitejs/plugin-vue-jsx 编译 .tsx 组件，但未安装 @vue/jsx-runtime 类型包，
 * 导致 vue-tsc 对 VirtualList 等 tsx 文件报 TS7026（JSX.IntrinsicElements 不存在）。
 * 这里做最小声明，让类型检查能覆盖 JSX 组件。
 */
declare namespace JSX {
  type Element = any
  interface ElementClass {
    $props: any
  }
  interface ElementAttributesProperty {
    $props: any
  }
  interface IntrinsicElements {
    [elem: string]: any
  }
}
