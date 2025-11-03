import { useState, useMemo, useEffect, useCallback } from 'react'
import Header from './components/Header/Header'
import Map from './components/Map/Map'
import StoreList from './components/StoreList/StoreList'
import { Store } from './types/store'
import { Location } from './types/common'
import { mockStores } from './data/mockStores'
import { geocodeAddress } from './utils/geocoding'
import { MAP_CONSTANTS } from './constants/map.constants'
import { fetchAndGetLandmarks, landmarkToStore } from './services/landmarksService'
import './styles/App.css'

function App() {
  const [selectedStore, setSelectedStore] = useState<Store | null>(null)
  const [stores] = useState<Store[]>(mockStores) // Dados mockados
  const [apiStores, setApiStores] = useState<Store[]>([]) // Landmarks da API
  const [viewMode, setViewMode] = useState<'map' | 'list'>('map')
  const [searchValue, setSearchValue] = useState('')
  const [searchLocation, setSearchLocation] = useState<Location | null>(null)

  // Debounce para geocoding
  useEffect(() => {
    if (!searchValue.trim()) {
      setSearchLocation(null)
      return
    }

    // Primeiro tenta buscar como texto simples nas lojas
    const searchLower = searchValue.toLowerCase().trim()
    const allStoresForSearch = [...stores, ...apiStores]
    const textMatch = allStoresForSearch.some(store => {
      const nameMatch = store.name.toLowerCase().includes(searchLower)
      const addressMatch = store.address.toLowerCase().includes(searchLower)
      return nameMatch || addressMatch
    })

    // Se encontrar match, não faz geocoding
    if (textMatch) {
      setSearchLocation(null)
      return
    }

    // Se não encontrar match e tiver mais de N caracteres, faz geocoding com debounce
    if (searchValue.trim().length >= MAP_CONSTANTS.MIN_SEARCH_LENGTH) {
      const timeoutId = setTimeout(async () => {
        try {
          const result = await geocodeAddress(searchValue)
          if (result) {
            setSearchLocation(result)
          } else {
            setSearchLocation(null)
          }
        } catch (error) {
          console.error('Erro ao buscar endereço:', error)
          setSearchLocation(null)
        }
      }, MAP_CONSTANTS.DEBOUNCE_DELAY_MS)

      return () => clearTimeout(timeoutId)
    } else {
      setSearchLocation(null)
    }
  }, [searchValue, stores, apiStores])


  // Combina stores mockadas com API stores
  const allStores = useMemo(() => {
    return [...stores, ...apiStores]
  }, [stores, apiStores])

  // Filtrar lojas baseado apenas na busca por texto
  const filteredStores = useMemo(() => {
    if (!searchValue.trim()) {
      return allStores
    }

    const searchLower = searchValue.toLowerCase().trim()
    
    return allStores.filter(store => {
      const nameMatch = store.name.toLowerCase().includes(searchLower)
      const addressMatch = store.address.toLowerCase().includes(searchLower)
      const geocodeMatch = store.geocode?.toLowerCase().includes(searchLower)
      const phoneMatch = store.phone?.toLowerCase().includes(searchLower)
      
      return nameMatch || addressMatch || geocodeMatch || phoneMatch
    })
  }, [allStores, searchValue])

  // Limpar seleção se a loja selecionada não estiver nos resultados filtrados
  useEffect(() => {
    if (selectedStore && !filteredStores.find(store => store.id === selectedStore.id)) {
      setSelectedStore(null)
    }
  }, [filteredStores, selectedStore])

  const handleSearch = useCallback((value: string) => {
    setSearchValue(value)
  }, [])

  const handleAddClick = async () => {
    // Implementar ação de adicionar loja
    console.log('Adicionar loja')
    
    // Se houver coordenadas de busca, buscar landmarks da API
    if (searchLocation) {
      try {
        console.log('🔍 Buscando landmarks da API Overpass...')
        const landmarks = await fetchAndGetLandmarks(
          searchLocation.latitude,
          searchLocation.longitude
        )
        
        // Converter landmarks para stores e adicionar
        const newStores = landmarks.map(landmarkToStore)
        setApiStores(prev => {
          // Evitar duplicatas por ID
          const existingIds = new Set(prev.map(s => s.id))
          const uniqueNew = newStores.filter(s => !existingIds.has(s.id))
          return [...prev, ...uniqueNew]
        })
        
        console.log(`✅ Adicionados ${newStores.length} landmarks ao mapa`)
      } catch (error) {
        console.error('❌ Erro ao buscar landmarks:', error)
      }
    }
  }

  return (
    <div className="app">
      <Header 
        viewMode={viewMode}
        onViewModeChange={setViewMode}
        onAddClick={handleAddClick}
      />
      <div className="app-content">
        {viewMode === 'map' && (
          <Map 
            stores={stores} 
            selectedStore={selectedStore}
            onStoreSelect={setSelectedStore}
            searchValue={searchValue}
            onSearchChange={handleSearch}
            searchLocation={searchLocation}
          />
        )}
        {viewMode === 'list' && (
          <div className="list-view-container">
            <StoreList 
              stores={filteredStores}
              selectedStore={selectedStore}
              onStoreSelect={setSelectedStore}
              isListView={true}
              searchValue={searchValue}
              onSearchChange={handleSearch}
            />
          </div>
        )}
        {viewMode === 'map' && (
          <StoreList 
            stores={filteredStores}
            selectedStore={selectedStore}
            onStoreSelect={setSelectedStore}
          />
        )}
      </div>
    </div>
  )
}

export default App
