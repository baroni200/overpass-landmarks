import { useState, useEffect, useCallback } from 'react'
import Header from './components/Header/Header'
import Map from './components/Map/Map'
import StoreList from './components/StoreList/StoreList'
import { Store } from './types/store'
import { Location } from './types/common'
import { geocodeAddress } from './utils/geocoding'
import { MAP_CONSTANTS } from './constants/map.constants'
import { fetchAndGetLandmarks, landmarkToStore } from './services/landmarksService'
import './styles/App.css'

function App() {
  const [selectedStore, setSelectedStore] = useState<Store | null>(null)
  const [stores, setStores] = useState<Store[]>([]) // Landmarks da API apenas
  const [viewMode, setViewMode] = useState<'map' | 'list'>('map')
  const [searchValue, setSearchValue] = useState('')
  const [searchLocation, setSearchLocation] = useState<Location | null>(null)

  // Geocoding simples - sempre faz busca por endereço
  useEffect(() => {
    if (!searchValue.trim()) {
      setSearchLocation(null)
      return
    }

    // Faz geocoding com debounce
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
  }, [searchValue])

  // Limpar seleção se a loja selecionada não estiver nos resultados filtrados
  useEffect(() => {
    if (selectedStore && !stores.find(store => store.id === selectedStore.id)) {
      setSelectedStore(null)
    }
  }, [stores, selectedStore])

  const handleSearch = useCallback((value: string) => {
    setSearchValue(value)
  }, [])

  const handleAddClick = async () => {
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
        setStores(prev => {
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
              stores={stores}
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
            stores={stores}
            selectedStore={selectedStore}
            onStoreSelect={setSelectedStore}
          />
        )}
      </div>
    </div>
  )
}

export default App
