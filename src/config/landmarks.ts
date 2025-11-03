// Configuração da API Overpass Landmarks
const API_BASE_URL = import.meta.env.VITE_LANDMARKS_API_URL || 'http://localhost:8080'

// Token de autenticação para o webhook (Bearer token)
const WEBHOOK_TOKEN = import.meta.env.VITE_LANDMARKS_WEBHOOK_TOKEN || 'supersecret'

if (!API_BASE_URL) {
  throw new Error(
    'VITE_LANDMARKS_API_URL não está configurada. Por favor, configure a variável de ambiente no arquivo .env'
  )
}

export const LANDMARKS_CONFIG = {
  baseUrl: API_BASE_URL,
  webhookToken: WEBHOOK_TOKEN,
  endpoints: {
    webhook: '/webhook',
    landmarks: '/landmarks',
  },
} as const

