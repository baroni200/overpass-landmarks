import { useState } from 'react'
import './SearchBar.css'

interface SearchBarProps {
  placeholder?: string
  value?: string
  onChange?: (value: string) => void
  onRefreshClick?: () => void
}

function SearchBar({ 
  placeholder = 'Search address', 
  value: controlledValue,
  onChange,
  onRefreshClick 
}: SearchBarProps) {
  const [internalValue, setInternalValue] = useState('')
  const value = controlledValue !== undefined ? controlledValue : internalValue

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value
    if (onChange) {
      onChange(newValue)
    } else {
      setInternalValue(newValue)
    }
  }

  const handleClear = () => {
    if (onChange) {
      onChange('')
    } else {
      setInternalValue('')
    }
  }

  return (
    <div className="search-bar">
      <div className="search-input-container">
        <div className="search-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M11 19C15.4183 19 19 15.4183 19 11C19 6.58172 15.4183 3 11 3C6.58172 3 3 6.58172 3 11C3 15.4183 6.58172 19 11 19Z" stroke="rgba(15, 15, 15, 0.6)" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M21 21L16.65 16.65" stroke="rgba(15, 15, 15, 0.6)" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
        <div className="search-input-wrapper">
          <input
            type="text"
            className="search-input"
            placeholder={placeholder}
            value={value}
            onChange={handleChange}
          />
        </div>
        {value && (
          <button 
            className="search-clear"
            onClick={handleClear}
            type="button"
            aria-label="Limpar busca"
          >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 6L6 18M6 6L18 18" stroke="rgba(15, 15, 15, 0.6)" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
        )}
      </div>
      {onRefreshClick && (
        <button 
          className="current-location-button"
          onClick={onRefreshClick}
          type="button"
          aria-label="Atualizar mapa"
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M1 4V10H7" stroke="#0056B8" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M23 20V14H17" stroke="#0056B8" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10M23 14L18.36 18.36A9 9 0 0 1 3.51 15" stroke="#0056B8" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
      )}
    </div>
  )
}

export default SearchBar

