import { Store } from '../types/store'

/**
 * Tipos para coordenadas e localização
 */
export type Coordinates = [longitude: number, latitude: number]

export interface Location {
  longitude: number
  latitude: number
  address: string
}

/**
 * Resultado de busca de lojas
 */
export interface StoreSearchResult {
  stores: Store[]
  searchLocation: Location | null
  isSearching: boolean
}

/**
 * Filtros de busca
 */
export interface StoreSearchFilters {
  name?: string
  address?: string
  phone?: string
  geocode?: string
}

