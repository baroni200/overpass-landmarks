/**
 * Tipos para a API Overpass Landmarks
 */

/**
 * Coordenadas
 */
export interface Coordinates {
  lat: number
  lng: number
}

/**
 * Resposta do webhook POST /webhook
 */
export interface WebhookResponse {
  key: Coordinates
  count: number
  radiusMeters: number
}

/**
 * Tags de um landmark (tags JSONB do OSM)
 */
export interface LandmarkTags {
  [key: string]: string | number | boolean | undefined
}

/**
 * Landmark individual retornado pela API
 */
export interface Landmark {
  id: string
  name: string
  osmType: 'node' | 'way' | 'relation'
  osmId: number
  lat: number
  lng: number
  tags: LandmarkTags
}

/**
 * Resultado da busca GET /landmarks
 */
export interface LandmarksResponse {
  key: Coordinates & { radiusMeters: number }
  source: 'cache' | 'db' | 'none'
  landmarks: Landmark[]
}

/**
 * Request para o webhook
 */
export interface WebhookRequest {
  lat: number
  lng: number
}

/**
 * Erro padrão da API
 */
export interface ApiError {
  error: string
  message: string
}

/**
 * Códigos de erro possíveis
 */
export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'INVALID_PARAMETER'
  | 'UNAUTHORIZED'
  | 'OVERPASS_ERROR'
  | 'WEBHOOK_PROCESSING_ERROR'
  | 'INTERNAL_ERROR'

