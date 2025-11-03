/**
 * Constantes relacionadas ao mapa
 */
export const MAP_CONSTANTS = {
  DEFAULT_ZOOM: 12,
  SELECTED_STORE_ZOOM: 15,
  SEARCH_LOCATION_ZOOM: 14,
  DEBOUNCE_DELAY_MS: 500,
  MIN_SEARCH_LENGTH: 3,
  POPUP_OFFSET: [0, -48] as [number, number],
  MAP_BOUNDS_PADDING: {
    top: 50,
    bottom: 50,
    left: 50,
    right: 50,
  },
} as const

