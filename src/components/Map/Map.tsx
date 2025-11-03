import { useEffect, useRef } from 'react'
import * as maptiler from '@maptiler/sdk'
import { Store } from '../../types/store'
import { MAP_CONFIG, MAPTILER_API_KEY } from '../../config/maptiler'
import { MAP_CONSTANTS } from '../../constants/map.constants'
import { Location } from '../../types/common'
import SearchBar from '../SearchBar/SearchBar'
import 'maplibre-gl/dist/maplibre-gl.css'
import './Map.css'

maptiler.config.apiKey = MAPTILER_API_KEY

interface MapProps {
  stores: Store[]
  selectedStore: Store | null
  onStoreSelect: (store: Store | null) => void
  searchValue?: string
  onSearchChange?: (value: string) => void
  searchLocation?: Location | null
}

function Map({ stores, selectedStore, onStoreSelect, searchValue, onSearchChange, searchLocation }: MapProps) {
  const mapContainer = useRef<HTMLDivElement>(null)
  const map = useRef<maptiler.Map | null>(null)
  const markers = useRef<maptiler.Marker[]>([])
  const popups = useRef<maptiler.Popup[]>([])
  const markerStoreMap = useRef<Map<string, { marker: maptiler.Marker, popup: maptiler.Popup }>>(
    new globalThis.Map<string, { marker: maptiler.Marker, popup: maptiler.Popup }>()
  )
  const searchMarker = useRef<maptiler.Marker | null>(null)

  useEffect(() => {
    if (!mapContainer.current) {
      console.error('Map container not found')
      return
    }

    console.log('Initializing map...', {
      container: mapContainer.current,
      style: MAP_CONFIG.style,
      center: MAP_CONFIG.center,
      zoom: MAP_CONFIG.zoom,
      apiKey: MAPTILER_API_KEY ? '***configured***' : '***missing***'
    })

    try {
      // Inicializar o mapa usando o estilo customizado do MapTiler
      map.current = new maptiler.Map({
        container: mapContainer.current,
        style: MAP_CONFIG.style,
        center: MAP_CONFIG.center,
        zoom: MAP_CONFIG.zoom,
      })

      // Adicionar event listener para quando o mapa carregar
      map.current.on('load', () => {
        console.log('Map loaded successfully')
        // Garantir que o mapa seja visível após carregar
        if (map.current) {
          map.current.resize()
        }
      })

      map.current.on('error', (e: any) => {
        console.error('Map error:', e)
        console.error('Error details:', {
          error: e.error,
          message: e.error?.message,
          type: e.error?.type
        })
        
        // Mostrar mensagem de erro mais clara se for problema de API key
        if (e.error?.message?.includes('Invalid key') || e.error?.message?.includes('Unauthorized')) {
          console.error('⚠️ ERRO: API Key do MapTiler inválida ou expirada!')
          console.error('Por favor, obtenha uma nova chave em: https://cloud.maptiler.com/account/keys/')
          console.error('E atualize o arquivo .env com a nova chave: VITE_MAPTILER_API_KEY=sua_nova_chave')
        }
      })

      map.current.on('style.load', () => {
        console.log('Map style loaded')
      })

      map.current.on('data', (e: any) => {
        if (e.dataType === 'style') {
          console.log('Style data loaded')
        }
      })

      return () => {
        map.current?.remove()
      }
    } catch (error) {
      console.error('Error initializing map:', error)
    }
  }, [])

  // Adicionar/atualizar marcadores quando as lojas mudarem
  useEffect(() => {
    if (!map.current) return

    // Aguardar o mapa carregar antes de adicionar marcadores
    const addMarkers = () => {
      console.log(`🗺️ Adicionando ${stores.length} marcadores no mapa`)
      
      // Remover marcadores e popups existentes
      markers.current.forEach(marker => marker.remove())
      popups.current.forEach(popup => popup.remove())
      markers.current = []
      popups.current = []
      markerStoreMap.current.clear()

      // Função para criar o SVG do pin com casa
      const createPinSVG = () => {
        return `
          <svg width="48" height="48" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Pin de endereço com casa">
            <defs>
              <style>
                :root{
                  --pin: #ff4d00;
                  --circle: #ffffff;
                  --house: #1967d2;
                }
              </style>
            </defs>
            <!-- Sombra suave opcional -->
            <ellipse cx="50" cy="88" rx="16" ry="6" fill="#000" opacity=".15"/>
            <!-- Corpo do pin (forma gota) -->
            <path d="M50,8
                     C32,8,18,22,18,40
                     c0,12,5,23,18,39
                     c6,7,9,10,14,10
                     s8-3,14-10
                     C77,63,82,52,82,40
                     C82,22,68,8,50,8Z"
                  fill="var(--pin)"/>
            <!-- Círculo branco interno -->
            <circle cx="50" cy="40" r="20" fill="var(--circle)"/>
            <!-- Ícone de casa (centrado no círculo) -->
            <g transform="translate(50,40)">
              <!-- telhado -->
              <path d="M-12,0 L0,-10 L12,0" fill="var(--house)"/>
              <!-- corpo da casa -->
              <rect x="-9" y="0" width="18" height="12" rx="1.5" fill="var(--house)"/>
              <!-- porta (recorte) -->
              <rect x="-3" y="5" width="6" height="7" fill="var(--circle)"/>
            </g>
          </svg>
        `
      }

      // Adicionar novos marcadores com labels
      console.log(`📍 Criando ${stores.length} marcadores...`)
      stores.forEach((store, index) => {
        // Log dos primeiros 3 para debug
        if (index < 3) {
          console.log(`   Marker ${index + 1}: ${store.name} (${store.latitude}, ${store.longitude})`)
        }
        
        // Criar elemento HTML customizado para o marcador com SVG do pin
        const el = document.createElement('div')
        el.className = 'custom-marker'
        el.innerHTML = createPinSVG()
        el.style.cursor = 'pointer'
        
        const marker = new maptiler.Marker({ 
          element: el,
          anchor: 'bottom'
        })
          .setLngLat([store.longitude, store.latitude])
          .addTo(map.current!)

        // Verificar se a loja está selecionada para adicionar classe CSS
        const isSelected = selectedStore?.id === store.id
        const containerClass = `store-popup-container${isSelected ? ' selected' : ''}`
        
        // Criar popup customizado com design completo
        const logoStyle = store.logo ? `background-image: url('${store.logo}');` : ''
        const popupHTML = `
          <div class="${containerClass}">
            <div class="store-popup-logo" style="${logoStyle}"></div>
            <div class="store-popup-info">
              <div class="store-popup-name">${store.name}</div>
              ${store.address ? `<div class="store-popup-address">${store.address}</div>` : ''}
              ${store.phone ? `<div class="store-popup-phone">${store.phone}</div>` : ''}
              <div class="store-popup-geocode">${store.geocode || store.address || 'N/A'}</div>
            </div>
            <button class="store-popup-close" aria-label="Close">
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M1 1L11 11M11 1L1 11" stroke="rgba(0, 0, 0, 0.54)" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
        `
        
        const popup = new maptiler.Popup({ 
          offset: [0, -48],
          closeButton: false,
          className: 'store-popup',
          anchor: 'bottom'
        })
          .setHTML(popupHTML)
        
        // Adicionar event listener para fechar o popup quando clicar no botão
        // Usar setTimeout para garantir que o DOM está renderizado
        const handlePopupOpen = () => {
          setTimeout(() => {
            const popupElement = popup.getElement()
            const closeButton = popupElement?.querySelector('.store-popup-close') as HTMLButtonElement
            if (closeButton) {
              closeButton.addEventListener('click', (e) => {
                e.stopPropagation()
                popup.remove()
                onStoreSelect(null)
              })
            }
          }, 10)
        }
        
        popup.on('open', handlePopupOpen)

        // Anexar popup ao marcador (mas não abrir automaticamente)
        marker.setPopup(popup)

        // Quando clicar no marcador, selecionar a loja (o popup será aberto pelo useEffect)
        el.addEventListener('click', (e) => {
          e.stopPropagation()
          e.preventDefault()
          onStoreSelect(store)
        })

        // Armazenar referência do marcador e popup para acessar depois
        markerStoreMap.current.set(store.id, { marker, popup })

        markers.current.push(marker)
        popups.current.push(popup)
      })
      
      console.log(`✅ ${markers.current.length} marcadores adicionados ao mapa`)
    }

    if (map.current.loaded()) {
      addMarkers()
    } else {
      map.current.once('load', addMarkers)
    }
  }, [stores, selectedStore, onStoreSelect])

  // Centralizar mapa na loja selecionada e abrir popup
  useEffect(() => {
    if (!map.current) return

    if (selectedStore) {
      // Encontrar o marcador correspondente
      const markerData = markerStoreMap.current.get(selectedStore.id)
      
      if (markerData) {
        // Fechar todos os outros popups primeiro
        popups.current.forEach(popup => {
          if (popup !== markerData.popup) {
            try {
              popup.remove()
            } catch (e) {
              // Popup pode já ter sido removido
            }
          }
        })

        // Atualizar o HTML do popup para refletir o estado selecionado
        const isSelected = true
        const containerClass = `store-popup-container${isSelected ? ' selected' : ''}`
        const logoStyle = selectedStore.logo ? `background-image: url('${selectedStore.logo}');` : ''
        const popupHTML = `
          <div class="${containerClass}">
            <div class="store-popup-logo" style="${logoStyle}"></div>
            <div class="store-popup-info">
              <div class="store-popup-name">${selectedStore.name}</div>
              ${selectedStore.address ? `<div class="store-popup-address">${selectedStore.address}</div>` : ''}
              ${selectedStore.phone ? `<div class="store-popup-phone">${selectedStore.phone}</div>` : ''}
              <div class="store-popup-geocode">${selectedStore.geocode || selectedStore.address || 'N/A'}</div>
            </div>
            <button class="store-popup-close" aria-label="Close">
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M1 1L11 11M11 1L1 11" stroke="rgba(0, 0, 0, 0.54)" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
        `
        markerData.popup.setHTML(popupHTML)
        
        // Adicionar event listener para fechar o popup quando clicar no botão
        setTimeout(() => {
          const popupElement = markerData.popup.getElement()
          const closeButton = popupElement?.querySelector('.store-popup-close') as HTMLButtonElement
          if (closeButton) {
            // Remover listeners anteriores para evitar duplicação
            const newCloseButton = closeButton.cloneNode(true) as HTMLButtonElement
            closeButton.parentNode?.replaceChild(newCloseButton, closeButton)
            newCloseButton.addEventListener('click', (e) => {
              e.stopPropagation()
              markerData.popup.remove()
              onStoreSelect(null)
            })
          }
        }, 10)

        // Centralizar no mapa
        map.current.flyTo({
          center: [selectedStore.longitude, selectedStore.latitude],
          zoom: MAP_CONSTANTS.SELECTED_STORE_ZOOM,
        })

        // Abrir popup após a animação do mapa terminar
        map.current.once('moveend', () => {
          setTimeout(() => {
            // Remover popup se já estiver aberto
            try {
              markerData.popup.remove()
            } catch (e) {
              // Popup pode não estar aberto
            }
            
            // Adicionar popup ao mapa diretamente
            markerData.popup.setLngLat([selectedStore.longitude, selectedStore.latitude])
            markerData.popup.addTo(map.current!)
          }, 100)
        })
      }
    } else {
      // Se nenhuma loja está selecionada, fechar todos os popups
      popups.current.forEach(popup => {
        try {
          popup.remove()
        } catch (e) {
          // Popup pode já ter sido removido
        }
      })
    }
  }, [selectedStore, onStoreSelect])

  // Criar marcador para o endereço buscado
  useEffect(() => {
    if (!map.current) return

    // Remover marcador anterior se existir
    if (searchMarker.current) {
      searchMarker.current.remove()
      searchMarker.current = null
    }

    if (searchLocation) {
      // Criar marcador diferente para o endereço buscado (azul)
      const searchMarkerEl = document.createElement('div')
      searchMarkerEl.className = 'search-marker'
      searchMarkerEl.innerHTML = `
        <svg width="48" height="48" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Endereço buscado">
          <defs>
            <style>
              :root{
                --pin: #0056B8;
                --circle: #ffffff;
              }
            </style>
          </defs>
          <ellipse cx="50" cy="88" rx="16" ry="6" fill="#000" opacity=".15"/>
          <path d="M50,8
                   C32,8,18,22,18,40
                   c0,12,5,23,18,39
                   c6,7,9,10,14,10
                   s8-3,14-10
                   C77,63,82,52,82,40
                   C82,22,68,8,50,8Z"
                fill="var(--pin)"/>
          <circle cx="50" cy="40" r="20" fill="var(--circle)"/>
          <circle cx="50" cy="40" r="12" fill="var(--pin)"/>
        </svg>
      `
      searchMarkerEl.style.cursor = 'pointer'

      searchMarker.current = new maptiler.Marker({
        element: searchMarkerEl,
        anchor: 'bottom'
      })
        .setLngLat([searchLocation.longitude, searchLocation.latitude])
        .addTo(map.current)

      // Criar popup para o endereço buscado
      const searchPopup = new maptiler.Popup({
        offset: MAP_CONSTANTS.POPUP_OFFSET,
        closeButton: false,
        className: 'store-popup',
        anchor: 'bottom'
      })
        .setHTML(`
          <div class="search-popup-container">
            <div class="search-popup-info">
              <div class="store-popup-name">${searchLocation.address}</div>
              <div class="store-popup-geocode">Endereço buscado</div>
            </div>
          </div>
        `)

      searchMarker.current.setPopup(searchPopup)

      // Mover mapa para o endereço buscado
      map.current.flyTo({
        center: [searchLocation.longitude, searchLocation.latitude],
        zoom: MAP_CONSTANTS.SEARCH_LOCATION_ZOOM,
      })

      // Abrir popup após animação
      map.current.once('moveend', () => {
        setTimeout(() => {
          searchMarker.current?.togglePopup()
        }, 300)
      })
    }
  }, [searchLocation])

  const handleRefresh = () => {
    if (map.current) {
      // Recarregar o mapa
      map.current.resize()
      // Voltar para a posição inicial ou centralizar nas lojas se houver
      if (stores.length > 0) {
        const bounds = new maptiler.LngLatBounds()
        stores.forEach(store => {
          bounds.extend([store.longitude, store.latitude])
        })
        map.current.fitBounds(bounds, {
          padding: MAP_CONSTANTS.MAP_BOUNDS_PADDING
        })
      } else {
        // Voltar para posição padrão
        map.current.flyTo({
          center: MAP_CONFIG.center,
          zoom: MAP_CONSTANTS.DEFAULT_ZOOM,
        })
      }
    }
  }

  return (
    <div className="map-wrapper">
      <div className="map-search-overlay">
        <SearchBar
          placeholder="Search address"
          value={searchValue}
          onChange={onSearchChange}
          onRefreshClick={handleRefresh}
        />
      </div>
      <div ref={mapContainer} className="map-container" />
    </div>
  )
}

export default Map


