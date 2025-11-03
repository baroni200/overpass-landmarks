import SegmentedControl from '../SegmentedControl/SegmentedControl'
import Button from '../Button/Button'
import './Header.css'

interface HeaderProps {
  viewMode: 'map' | 'list'
  onViewModeChange: (mode: 'map' | 'list') => void
  onAddClick?: () => void
}

function Header({ viewMode, onViewModeChange, onAddClick }: HeaderProps) {
  return (
    <header className="header">
      <div className="header-container">
        <div className="header-left">
          <div className="header-logo">
            <h1 className="header-title">MAP VIEW</h1>
          </div>
          <div className="header-tools">
            <SegmentedControl
              options={[
                { value: 'map', label: 'Map View' },
                { value: 'list', label: 'List View' },
              ]}
              value={viewMode}
              onChange={(value) => onViewModeChange(value as 'map' | 'list')}
            />
          </div>
        </div>
        <div className="header-actions">
          <Button variant="primary" onClick={onAddClick}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 5V19M5 12H19" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"/>
            </svg>
            <span>ADD STORE</span>
          </Button>
        </div>
      </div>
    </header>
  )
}

export default Header

