import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, gatewayUrl } from '../api.js'
import Icon from '../components/Icon.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { dateTime, money, number } from '../format.js'

export default function Dashboard({ notify }) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [lbResults, setLbResults] = useState([])
  const [lbBusy, setLbBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [products, inventory, warehouses, lowStock, summary, orders] = await Promise.all([
        api('/products'),
        api('/inventory'),
        api('/warehouses'),
        api('/inventory/low-stock'),
        api('/orders/summary'),
        api('/orders')
      ])
      setData({ products, inventory, warehouses, lowStock, summary, orders })
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const lookups = useMemo(() => {
    const products = new Map((data?.products || []).map((p) => [p.id, p]))
    const warehouses = new Map((data?.warehouses || []).map((w) => [w.id, w]))
    return { products, warehouses }
  }, [data])

  const stats = useMemo(() => {
    if (!data) return []
    const available = data.inventory.reduce((sum, row) => sum + row.availableQuantity, 0)
    return [
      {
        label: 'Catalog products',
        value: number(data.products.length),
        hint: 'shared product service',
        icon: 'products',
        tone: 'indigo'
      },
      {
        label: 'Warehouses',
        value: number(data.warehouses.length),
        hint: 'regional hubs',
        icon: 'warehouses',
        tone: 'teal'
      },
      {
        label: 'Available units',
        value: number(available),
        hint: 'on hand minus reserved',
        icon: 'inventory',
        tone: 'blue'
      },
      {
        label: 'Low stock alerts',
        value: number(data.lowStock.length),
        hint: 'at or below reorder level',
        icon: 'alert',
        tone: data.lowStock.length ? 'amber' : 'green'
      },
      {
        label: 'Orders placed',
        value: number(data.summary.totalOrders),
        hint: `${number(data.summary.confirmed)} confirmed`,
        icon: 'orders',
        tone: 'slate'
      },
      {
        label: 'Confirmed revenue',
        value: money(data.summary.confirmedRevenue),
        hint: 'cancelled orders excluded',
        icon: 'check',
        tone: 'green'
      }
    ]
  }, [data])

  const recentOrders = useMemo(() => {
    if (!data) return []
    return [...data.orders]
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
      .slice(0, 5)
  }, [data])

  async function probeLoadBalancer() {
    setLbBusy(true)
    setLbResults([])
    try {
      const results = []
      for (let i = 0; i < 6; i += 1) {
        const result = await api('/inventory/instance')
        results.push({ port: result.instance, request: i + 1 })
        setLbResults([...results])
        await new Promise((resolve) => setTimeout(resolve, 180))
      }
      notify('Load balancer probe completed across inventory instances.')
    } catch (err) {
      notify(err.message, 'error')
    } finally {
      setLbBusy(false)
    }
  }

  if (loading) {
    return (
      <div className="state">
        <span className="spinner" />
        <p>Loading live inventory position…</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="state state-error">
        <Icon name="alert" size={26} />
        <h3>Could not load the dashboard</h3>
        <p>{error}</p>
        <button type="button" className="btn btn-primary" onClick={load}>
          Try again
        </button>
      </div>
    )
  }

  return (
    <div className="stack">
      <div className="stat-grid">
        {stats.map((stat) => (
          <article key={stat.label} className={`stat-card tone-${stat.tone}`}>
            <span className="stat-icon"><Icon name={stat.icon} size={20} /></span>
            <div>
              <span className="stat-label">{stat.label}</span>
              <strong>{stat.value}</strong>
              <small>{stat.hint}</small>
            </div>
          </article>
        ))}
      </div>

      <div className="grid-2">
        <section className="panel">
          <header className="panel-header">
            <div>
              <h2>Replenishment alerts</h2>
              <p>Stock at or below the configured reorder level.</p>
            </div>
            <span className={`count-pill ${data.lowStock.length ? 'warn' : 'ok'}`}>
              {data.lowStock.length} alert{data.lowStock.length === 1 ? '' : 's'}
            </span>
          </header>

          {data.lowStock.length === 0 ? (
            <div className="empty">
              <Icon name="check" size={24} />
              <p>Every product is above its reorder level.</p>
            </div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Product</th>
                    <th>Warehouse</th>
                    <th className="num">Available</th>
                    <th className="num">Reorder at</th>
                  </tr>
                </thead>
                <tbody>
                  {data.lowStock.map((row) => (
                    <tr key={row.id}>
                      <td>
                        <div className="cell-title">
                          {lookups.products.get(row.productId)?.name || `Product #${row.productId}`}
                        </div>
                        <div className="cell-sub">
                          {lookups.products.get(row.productId)?.sku || '—'}
                        </div>
                      </td>
                      <td>
                        {lookups.warehouses.get(row.warehouseId)?.code || `WH-${row.warehouseId}`}
                      </td>
                      <td className="num">
                        <span className="danger-text">{row.availableQuantity}</span>
                      </td>
                      <td className="num">{row.reorderLevel}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="panel">
          <header className="panel-header">
            <div>
              <h2>Load balancing probe</h2>
              <p>Repeated calls through the gateway should hit different instances.</p>
            </div>
          </header>

          <div className="lb-body">
            <div className="lb-endpoint">
              <code>GET {gatewayUrl}/inventory/instance</code>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={probeLoadBalancer}
                disabled={lbBusy}
              >
                <Icon name="refresh" size={15} />
                {lbBusy ? 'Probing…' : 'Run probe'}
              </button>
            </div>

            {lbResults.length === 0 ? (
              <p className="muted">
                Start a second inventory instance on port 8093 (<code>scripts\start-all.ps1</code>)
                and run the probe to see round-robin routing.
              </p>
            ) : (
              <div className="lb-results">
                {lbResults.map((result) => (
                  <span key={result.request} className="lb-chip">
                    <small>#{result.request}</small> :{result.port}
                  </span>
                ))}
              </div>
            )}

            <div className="lb-legend">
              <span className="live-dot" /> Eureka registry + Spring Cloud LoadBalancer
            </div>
          </div>
        </section>
      </div>

      <section className="panel">
        <header className="panel-header">
          <div>
            <h2>Recent orders</h2>
            <p>Latest activity from the order service.</p>
          </div>
        </header>

        {recentOrders.length === 0 ? (
          <div className="empty">
            <Icon name="orders" size={24} />
            <p>No orders yet. Place one from the Orders page.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Customer</th>
                  <th>Product</th>
                  <th className="num">Qty</th>
                  <th className="num">Total</th>
                  <th>Status</th>
                  <th>Placed</th>
                </tr>
              </thead>
              <tbody>
                {recentOrders.map((order) => (
                  <tr key={order.id}>
                    <td className="mono">{order.orderNumber}</td>
                    <td>{order.customerName}</td>
                    <td>{order.productName}</td>
                    <td className="num">{order.quantity}</td>
                    <td className="num">{money(order.totalAmount)}</td>
                    <td><StatusBadge status={order.status} /></td>
                    <td className="muted">{dateTime(order.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
