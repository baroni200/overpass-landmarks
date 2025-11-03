/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_MAPTILER_API_KEY: string
  readonly VITE_LANDMARKS_API_URL?: string
  readonly VITE_LANDMARKS_WEBHOOK_TOKEN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

