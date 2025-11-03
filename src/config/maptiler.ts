// Configuração do MapTiler
const API_KEY = import.meta.env.VITE_MAPTILER_API_KEY

if (!API_KEY) {
  throw new Error(
    'VITE_MAPTILER_API_KEY não está configurada. Por favor, configure a variável de ambiente no arquivo .env'
  )
}

export const MAPTILER_API_KEY = API_KEY

// URL do estilo customizado criado no MapTiler
const MAP_STYLE_ID = '019a377c-cae1-7c25-92b2-288a6272d4f0'
export const MAP_STYLE_URL = `https://api.maptiler.com/maps/${MAP_STYLE_ID}/style.json?key=${API_KEY}`

export const MAP_CONFIG = {
  style: MAP_STYLE_URL,
  center: [-46.6333, -23.5505] as [number, number], // São Paulo como padrão
  zoom: 12,
} as const
