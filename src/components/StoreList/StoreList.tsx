import { Store } from '../../types/store'
import StoreCard from '../StoreCard/StoreCard'
import SearchBar from '../SearchBar/SearchBar'
import './StoreList.css'

interface StoreListProps {
  stores: Store[]
  selectedStore: Store | null
  onStoreSelect: (store: Store | null) => void
  isListView?: boolean
  searchValue?: string
  onSearchChange?: (value: string) => void
}

function StoreList({ 
  stores, 
  selectedStore, 
  onStoreSelect, 
  isListView = false,
  searchValue,
  onSearchChange
}: StoreListProps) {
  // Log para debug
  console.log(`📋 StoreList renderizado: ${stores.length} lojas`, {
    isListView,
    searchValue,
    stores: stores.slice(0, 5).map(s => ({ name: s.name, id: s.id.substring(0, 20) }))
  })

  return (
    <div className={`store-list ${isListView ? 'list-view-mode' : ''}`}>
      {isListView && (
        <div className="store-list-search">
          <SearchBar
            placeholder="Search address"
            value={searchValue}
            onChange={onSearchChange}
          />
        </div>
      )}
      <div className="store-list-title">
        STORE LIST ({stores.length})
      </div>
      <div className="store-list-content">
        {stores.length === 0 ? (
          <div className="empty-state">
            <p>Nenhuma loja encontrada</p>
          </div>
        ) : (
          <div className={`store-items ${isListView ? 'grid-layout' : ''}`}>
            {stores.map(store => (
              <StoreCard
                key={store.id}
                store={store}
                isSelected={selectedStore?.id === store.id}
                onClick={() => onStoreSelect(store)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

export default StoreList

