import { MAPTILER_API_KEY } from '../config/maptiler'

export interface GeocodeResult {
  longitude: number
  latitude: number
  address: string
}

/**
 * Calcula a distância em quilômetros entre duas coordenadas usando a fórmula de Haversine
 * @param lat1 Latitude do primeiro ponto
 * @param lon1 Longitude do primeiro ponto
 * @param lat2 Latitude do segundo ponto
 * @param lon2 Longitude do segundo ponto
 * @returns Distância em quilômetros
 */
export function calculateDistance(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const R = 6371 // Raio da Terra em km
  const dLat = (lat2 - lat1) * Math.PI / 180
  const dLon = (lon2 - lon1) * Math.PI / 180
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return R * c
}

/**
 * Faz geocoding de um endereço usando a API do MapTiler
 * @param address Endereço a ser geocodificado
 * @returns Promise com o resultado do geocoding ou null se não encontrar
 * @throws Erro se a API key não estiver configurada ou se houver erro na requisição
 */
export async function geocodeAddress(address: string): Promise<GeocodeResult | null> {
  if (!MAPTILER_API_KEY) {
    throw new Error('MAPTILER_API_KEY não está configurada')
  }

  if (!address || address.trim().length === 0) {
    throw new Error('Endereço não pode ser vazio')
  }

  try {
    const url = `https://api.maptiler.com/geocoding/${encodeURIComponent(address.trim())}.json?key=${MAPTILER_API_KEY}&limit=1`
    
    const response = await fetch(url)
    
    if (!response.ok) {
      throw new Error(`Erro na requisição de geocoding: ${response.status} ${response.statusText}`)
    }
    
    const data = await response.json()
    
    if (data.features && data.features.length > 0) {
      const feature = data.features[0]
      return {
        longitude: feature.geometry.coordinates[0],
        latitude: feature.geometry.coordinates[1],
        address: feature.place_name || address
      }
    }
    
    return null
  } catch (error) {
    // Re-throw com contexto adicional
    if (error instanceof Error) {
      throw new Error(`Erro ao fazer geocoding: ${error.message}`)
    }
    throw new Error('Erro desconhecido ao fazer geocoding')
  }
}
