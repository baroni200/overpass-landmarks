import { LANDMARKS_CONFIG } from '../config/landmarks'
import {
  WebhookRequest,
  WebhookResponse,
  LandmarksResponse,
  ApiError,
  Landmark,
} from '../types/landmark'

/**
 * Classe de erro customizada para erros da API Landmarks
 */
export class LandmarksApiError extends Error {
  constructor(
    message: string,
    public status?: number,
    public code?: string
  ) {
    super(message)
    this.name = 'LandmarksApiError'
  }
}

/**
 * Faz requisição ao endpoint POST /webhook
 * Envia coordenadas para processar e buscar landmarks
 */
export async function triggerWebhook(
  lat: number,
  lng: number
): Promise<WebhookResponse> {
  try {
    const url = `${LANDMARKS_CONFIG.baseUrl}${LANDMARKS_CONFIG.endpoints.webhook}`
    
    const request: WebhookRequest = { lat, lng }
    
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${LANDMARKS_CONFIG.webhookToken}`,
      },
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      const errorData: ApiError = await response.json()
      throw new LandmarksApiError(
        errorData.message || 'Erro ao processar webhook',
        response.status,
        errorData.error
      )
    }

    const data: WebhookResponse = await response.json()
    return data
  } catch (error) {
    if (error instanceof LandmarksApiError) {
      throw error
    }
    
    if (error instanceof Error) {
      throw new LandmarksApiError(`Erro de rede: ${error.message}`)
    }
    
    throw new LandmarksApiError('Erro desconhecido ao processar webhook')
  }
}

/**
 * Busca landmarks por coordenadas GET /landmarks
 * Retorna landmarks armazenados (cache ou DB)
 */
export async function getLandmarks(
  lat: number,
  lng: number
): Promise<LandmarksResponse> {
  try {
    const url = `${LANDMARKS_CONFIG.baseUrl}${LANDMARKS_CONFIG.endpoints.landmarks}?lat=${lat}&lng=${lng}`
    
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
      },
    })

    if (!response.ok) {
      const errorData: ApiError = await response.json()
      throw new LandmarksApiError(
        errorData.message || 'Erro ao buscar landmarks',
        response.status,
        errorData.error
      )
    }

    const data: LandmarksResponse = await response.json()
    return data
  } catch (error) {
    if (error instanceof LandmarksApiError) {
      throw error
    }
    
    if (error instanceof Error) {
      throw new LandmarksApiError(`Erro de rede: ${error.message}`)
    }
    
    throw new LandmarksApiError('Erro desconhecido ao buscar landmarks')
  }
}

/**
 * Combina operações: primeiro trigger webhook, depois busca landmarks
 * Útil para garantir que dados estejam atualizados
 */
export async function fetchAndGetLandmarks(
  lat: number,
  lng: number
): Promise<Landmark[]> {
  try {
    // Primeiro, dispara o webhook para processar
    console.log('🔍 Disparando webhook para processar coordenadas...')
    const webhookResult = await triggerWebhook(lat, lng)
    console.log(`✅ Webhook processado: ${webhookResult.count} landmarks encontrados`)

    // Depois, busca os landmarks processados
    console.log('📥 Buscando landmarks processados...')
    const landmarksResult = await getLandmarks(lat, lng)
    console.log(`✅ Landmarks retornados: ${landmarksResult.landmarks.length} itens (source: ${landmarksResult.source})`)

    return landmarksResult.landmarks
  } catch (error) {
    console.error('❌ Erro ao buscar landmarks:', error)
    if (error instanceof LandmarksApiError) {
      throw error
    }
    throw new LandmarksApiError('Erro desconhecido ao buscar landmarks')
  }
}

/**
 * Converte um Landmark da API para o formato Store do app
 */
export function landmarkToStore(landmark: Landmark): import('../types/store').Store {
  const name = landmark.name || (landmark.tags.name ? String(landmark.tags.name) : undefined) || 'Local sem nome'
  
  // Criar endereço a partir dos tags ou coordenadas
  let address = name
  if (landmark.tags['addr:street']) {
    const street = String(landmark.tags['addr:street'])
    const number = landmark.tags['addr:housenumber']
    address = number ? `${street}, ${String(number)}` : street
  }

  return {
    id: landmark.id,
    name,
    address: address,
    latitude: landmark.lat,
    longitude: landmark.lng,
    geocode: `${landmark.lat}, ${landmark.lng}`,
    // Campos opcionais podem vir dos tags
    website: landmark.tags.website ? String(landmark.tags.website) : undefined,
    // Gerar logo SVG com as iniciais
    logo: createLogoFromName(name),
  }
}

/**
 * Função auxiliar para gerar logo SVG baseado no nome
 */
function createLogoFromName(name: string): string {
  const initials = name
    .split(' ')
    .slice(0, 2)
    .map(word => word.charAt(0).toUpperCase())
    .join('')
    .substring(0, 2) || 'LM'
  
  // Cores aleatórias baseadas no hash do nome
  const colors = [
    '#0056B8', '#DC143C', '#228B22', '#FF8C00', '#9370DB',
    '#20B2AA', '#FF1493', '#4169E1', '#FF6347', '#32CD32'
  ]
  const colorIndex = name.length % colors.length
  const color = colors[colorIndex]
  
  const svgContent = `<svg width="76" height="76" viewBox="0 0 76 76" fill="none" xmlns="http://www.w3.org/2000/svg"><rect width="76" height="76" rx="38" fill="${color}"/><text x="38" y="38" font-family="Arial, sans-serif" font-size="32" font-weight="bold" fill="white" text-anchor="middle" dominant-baseline="central">${initials}</text></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svgContent)}`
}

