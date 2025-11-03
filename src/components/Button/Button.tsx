import { ReactNode } from 'react'
import './Button.css'

interface ButtonProps {
  variant?: 'primary' | 'secondary' | 'ghost'
  children: ReactNode
  onClick?: () => void
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
}

function Button({ 
  variant = 'primary', 
  children, 
  onClick, 
  type = 'button',
  disabled = false 
}: ButtonProps) {
  return (
    <button
      className={`button button-${variant}`}
      onClick={onClick}
      type={type}
      disabled={disabled}
    >
      {children}
    </button>
  )
}

export default Button

