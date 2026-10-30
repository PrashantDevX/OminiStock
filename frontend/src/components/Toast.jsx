import Icon from './Icon.jsx'

const icons = { success: 'check', error: 'alert', info: 'info' }

export default function Toast({ toast, onClose }) {
  if (!toast) return null

  return (
    <div className={`toast toast-${toast.type || 'success'}`} role="status">
      <span className="toast-icon">
        <Icon name={icons[toast.type] || 'info'} size={18} />
      </span>
      <span>{toast.message}</span>
      <button type="button" className="toast-close" onClick={onClose} aria-label="Dismiss">
        <Icon name="close" size={15} />
      </button>
    </div>
  )
}
