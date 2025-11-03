import { Store } from '../../types/store'
import './StoreCard.css'

interface StoreCardProps {
  store: Store
  isSelected?: boolean
  onClick?: () => void
}

function StoreCard({ store, isSelected = false, onClick }: StoreCardProps) {
  return (
    <div 
      className={`store-card ${isSelected ? 'selected' : ''}`}
      onClick={onClick}
    >
      <div className="store-card-bg">
        <div 
          className="store-logo"
          style={store.logo ? { backgroundImage: `url('${store.logo}')` } : {}}
        >
          {!store.logo && (
            <div className="store-logo-placeholder" />
          )}
        </div>
        <div className="store-info">
          <div className="store-name">{store.name}</div>
          <div className="store-address">{store.address}</div>
          {store.phone && (
            <div className="store-phone">{store.phone}</div>
          )}
        </div>
      </div>
    </div>
  )
}

export default StoreCard

