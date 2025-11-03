export interface Store {
  id: string
  name: string
  address: string
  latitude: number
  longitude: number
  phone?: string
  email?: string
  website?: string
  geocode?: string // Código de geolocalização
  logo?: string // URL ou data URI do logotipo
}

