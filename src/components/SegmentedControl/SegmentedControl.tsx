import './SegmentedControl.css'

interface SegmentedControlOption {
  value: string
  label: string
}

interface SegmentedControlProps {
  options: SegmentedControlOption[]
  value: string
  onChange: (value: string) => void
}

function SegmentedControl({ options, value, onChange }: SegmentedControlProps) {
  return (
    <div className="segmented-control-container">
      <div className="segmented-control-inner">
        {options.map((option) => (
          <button
            key={option.value}
            className={`segment-toggle ${value === option.value ? 'active' : ''}`}
            onClick={() => onChange(option.value)}
            type="button"
          >
            <span className="segment-label">{option.label}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

export default SegmentedControl

