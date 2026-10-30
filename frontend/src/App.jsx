import { useCallback, useEffect, useState } from 'react'
import { clearSession, getSession, saveSession } from './auth.js'
import { setUnauthorizedHandler } from './api.js'
import Icon from './components/Icon.jsx'
import Toast from './components/Toast.jsx'
import Login from './pages/Login.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Products from './pages/Products.jsx'
import Inventory from './pages/Inventory.jsx'
import Orders from './pages/Orders.jsx'
import Warehouses from './pages/Warehouses.jsx'

const NAV = [
  { id: 'dashboard', label: 'Dashboard', icon: 'dashboard' },
  { id: 'products', label: 'Products', icon: 'products' },
  { id: 'inventory', label: 'Inventory', icon: 'inventory' },
  { id: 'orders', label: 'Orders', icon: 'orders' },
  { id: 'warehouses', label: 'Warehouses', icon: 'warehouses' }
]

const PAGE_META = {
  dashboard: {
    eyebrow: 'Control tower',
    title: 'Operations overview',
    subtitle: 'Live stock position, replenishment alerts and order activity.'
  },
  products: {
    eyebrow: 'Catalog',
    title: 'Product catalog',
    subtitle: 'Maintain the products that flow through every distribution hub.'
  },
  inventory: {
    eyebrow: 'Stock control',
    title: 'Inventory levels',
    subtitle: 'Monitor on-hand, reserved and available units per warehouse.'
  },
  orders: {
    eyebrow: 'Fulfilment',
    title: 'Order management',
    subtitle: 'Place orders and watch stock reservations reconcile automatically.'
  },
  warehouses: {
    eyebrow: 'Network',
    title: 'Warehouse network',
    subtitle: 'Regional hubs served by the OmniStock platform.'
  }
}

export default function App() {
  const [session, setSession] = useState(() => getSession())
  const [page, setPage] = useState('dashboard')
  const [toast, setToast] = useState(null)

  const notify = useCallback((message, type = 'success') => {
    setToast({ message, type, id: Date.now() })
  }, [])

  const logout = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession()
      setSession(null)
    })
  }, [])

  useEffect(() => {
    if (!toast) return undefined
    const timer = setTimeout(() => setToast(null), 4200)
    return () => clearTimeout(timer)
  }, [toast])

  if (!session?.token || !session?.user) {
    return (
      <>
        <Login
          onLogin={(auth) => {
            saveSession(auth)
            setSession(auth)
            setPage('dashboard')
            notify(`Welcome back, ${auth.user.fullName || auth.user.username}`)
          }}
          notify={notify}
        />
        <Toast toast={toast} onClose={() => setToast(null)} />
      </>
    )
  }

  const user = session.user
  const meta = PAGE_META[page] || PAGE_META.dashboard
  const initials = (user.fullName || user.username || 'U')
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  const common = { user, notify }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">OS</div>
          <div>
            <strong>OmniStock</strong>
            <span>Inventory control</span>
          </div>
        </div>

        <nav className="nav">
          <span className="nav-label">Workspace</span>
          {NAV.map((item) => (
            <button
              type="button"
              key={item.id}
              className={`nav-item ${page === item.id ? 'active' : ''}`}
              onClick={() => setPage(item.id)}
            >
              <Icon name={item.icon} size={19} />
              <span>{item.label}</span>
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="user-chip">
            <span className="avatar">{initials}</span>
            <div>
              <strong>{user.fullName || user.username}</strong>
              <span>{user.role}</span>
            </div>
          </div>
          <button type="button" className="logout-btn" onClick={logout}>
            <Icon name="logout" size={17} />
            Sign out
          </button>
        </div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <span className="eyebrow">{meta.eyebrow}</span>
            <h1>{meta.title}</h1>
            <p>{meta.subtitle}</p>
          </div>
          <div className="topbar-actions">
            <a
              className="ghost-link"
              href="http://localhost:8761"
              target="_blank"
              rel="noreferrer"
            >
              Eureka registry
            </a>
            <span className="role-pill">{user.role}</span>
          </div>
        </header>

        <div className="content">
          {page === 'dashboard' && <Dashboard {...common} />}
          {page === 'products' && <Products {...common} />}
          {page === 'inventory' && <Inventory {...common} />}
          {page === 'orders' && <Orders {...common} />}
          {page === 'warehouses' && <Warehouses {...common} />}
        </div>
      </main>

      <Toast toast={toast} onClose={() => setToast(null)} />
    </div>
  )
}
