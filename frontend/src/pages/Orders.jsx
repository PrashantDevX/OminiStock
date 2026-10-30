import { useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import Icon from '../components/Icon.jsx'
import Modal from '../components/Modal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { dateTime, money, number } from '../format.js'

const EMPTY_ORDER = {
  customerName: '',
  customerEmail: '',
  productId: '',
  warehouseId: '',
  quantity: '1'
}

const FILTERS = ['ALL', 'CONFIRMED', 'REJECTED', 'CANCELLED']

export default function Orders({ notify }) {
  const [orders, setOrders] = useState([])
  const [products, setProducts] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [inventory, setInventory] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filter, setFilter] = useState('ALL')
  const [editorOpen, setEditorOpen] = useState(false)
  const [form, setForm] = useState(EMPTY_ORDER)
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  async function load() {
    setLoading(true)
    setError('')
    try {
      const [orderList, productList, warehouseList, stock] = await Promise.all([
        api('/orders'),
        api('/products'),
        api('/warehouses'),
        api('/inventory')
      ])
      setOrders(orderList)
      setProducts(productList)
      setWarehouses(warehouseList)
      setInventory(stock)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const productMap = useMemo(
    () => new Map(products.map((product) => [product.id, product])),
    [products]
  )
  const warehouseMap = useMemo(
    () => new Map(warehouses.map((warehouse) => [warehouse.id, warehouse])),
    [warehouses]
  )

  const summary = useMemo(() => {
    return orders.reduce(
      (acc, order) => {
        acc.all += 1
        if (order.status === 'CONFIRMED') {
          acc.confirmed += 1
          acc.revenue += order.totalAmount
        }
        if (order.status === 'REJECTED') acc.rejected += 1
        if (order.status === 'CANCELLED') acc.cancelled += 1
        return acc
      },
      { all: 0, confirmed: 0, rejected: 0, cancelled: 0, revenue: 0 }
    )
  }, [orders])

  const filtered = useMemo(() => {
    const list = filter === 'ALL' ? orders : orders.filter((order) => order.status === filter)
    return [...list].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  }, [orders, filter])

  const selectedProduct = productMap.get(Number(form.productId))
  const selectedWarehouse = warehouseMap.get(Number(form.warehouseId))
  const availableForSelection = useMemo(() => {
    if (!form.productId || !form.warehouseId) return null
    const row = inventory.find(
      (item) =>
        item.productId === Number(form.productId) &&
        item.warehouseId === Number(form.warehouseId)
    )
    return row ? row.availableQuantity : 0
  }, [inventory, form.productId, form.warehouseId])

  function openCreate() {
    setForm({
      ...EMPTY_ORDER,
      productId: products[0] ? String(products[0].id) : '',
      warehouseId: warehouses[0] ? String(warehouses[0].id) : ''
    })
    setFormError('')
    setEditorOpen(true)
  }

  async function placeOrder(event) {
    event.preventDefault()
    setSaving(true)
    setFormError('')

    try {
      const order = await api('/orders', {
        method: 'POST',
        body: {
          productId: Number(form.productId),
          warehouseId: Number(form.warehouseId),
          quantity: Number(form.quantity),
          customerName: form.customerName,
          customerEmail: form.customerEmail || null
        }
      })
      notify(`Order ${order.orderNumber} confirmed and stock reserved.`)
      setEditorOpen(false)
      await load()
    } catch (err) {
      if (err.status === 409 && err.data?.orderNumber) {
        notify(`Order ${err.data.orderNumber} rejected — insufficient available stock.`, 'error')
        setEditorOpen(false)
        await load()
      } else {
        setFormError(err.message)
      }
    } finally {
      setSaving(false)
    }
  }

  async function cancel(order) {
    if (!window.confirm(`Cancel ${order.orderNumber}? Reserved stock will be released.`)) return
    try {
      await api(`/orders/${order.id}/cancel`, { method: 'POST' })
      notify(`Order ${order.orderNumber} cancelled and stock released.`)
      await load()
    } catch (err) {
      notify(err.message, 'error')
    }
  }

  const estimatedTotal = selectedProduct
    ? selectedProduct.price * Number(form.quantity || 0)
    : 0

  return (
    <div className="stack">
      <div className="mini-stats">
        <div><span>Total orders</span><strong>{number(summary.all)}</strong></div>
        <div><span>Confirmed</span><strong>{number(summary.confirmed)}</strong></div>
        <div className={summary.rejected ? 'warn' : ''}>
          <span>Rejected</span><strong>{number(summary.rejected)}</strong>
        </div>
        <div><span>Revenue</span><strong>{money(summary.revenue)}</strong></div>
      </div>

      <section className="panel">
        <header className="panel-header panel-header-row">
          <div>
            <h2>Order ledger</h2>
            <p>Order → Inventory → Product calls happen during placement.</p>
          </div>
          <div className="toolbar">
            <div className="tabs">
              {FILTERS.map((value) => (
                <button
                  type="button"
                  key={value}
                  className={filter === value ? 'active' : ''}
                  onClick={() => setFilter(value)}
                >
                  {value === 'ALL' ? 'All' : value.charAt(0) + value.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
            <button type="button" className="btn btn-primary" onClick={openCreate}>
              <Icon name="plus" size={16} />
              Place order
            </button>
          </div>
        </header>

        {loading ? (
          <div className="state"><span className="spinner" /><p>Loading orders…</p></div>
        ) : error ? (
          <div className="state state-error">
            <Icon name="alert" size={24} />
            <p>{error}</p>
            <button type="button" className="btn btn-secondary" onClick={load}>Retry</button>
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty">
            <Icon name="orders" size={24} />
            <p>No {filter === 'ALL' ? '' : filter.toLowerCase()} orders yet.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Customer</th>
                  <th>Product</th>
                  <th>Warehouse</th>
                  <th className="num">Qty</th>
                  <th className="num">Total</th>
                  <th>Status</th>
                  <th>Placed</th>
                  <th className="right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((order) => (
                  <tr key={order.id}>
                    <td className="mono">{order.orderNumber}</td>
                    <td>
                      <div className="cell-title">{order.customerName}</div>
                      <div className="cell-sub">{order.customerEmail || '—'}</div>
                    </td>
                    <td>
                      <div className="cell-title">{order.productName}</div>
                      <div className="cell-sub">#{order.productId}</div>
                    </td>
                    <td>{warehouseMap.get(order.warehouseId)?.code || `WH-${order.warehouseId}`}</td>
                    <td className="num">{order.quantity}</td>
                    <td className="num">{money(order.totalAmount)}</td>
                    <td><StatusBadge status={order.status} /></td>
                    <td className="muted">{dateTime(order.createdAt)}</td>
                    <td className="right">
                      {order.status === 'CONFIRMED' ? (
                        <button type="button" className="btn btn-tiny danger-text" onClick={() => cancel(order)}>
                          Cancel
                        </button>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {editorOpen && (
        <Modal
          title="Place an order"
          subtitle="Stock is reserved atomically before the order is confirmed."
          onClose={() => setEditorOpen(false)}
          footer={
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setEditorOpen(false)}>
                Cancel
              </button>
              <button type="submit" form="order-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Placing order…' : `Confirm order · ${money(estimatedTotal)}`}
              </button>
            </>
          }
        >
          <form id="order-form" className="form" onSubmit={placeOrder}>
            <div className="form-grid">
              <label className="field">
                <span>Customer name</span>
                <input
                  value={form.customerName}
                  onChange={(event) => setForm({ ...form, customerName: event.target.value })}
                  placeholder="Customer name"
                  required
                />
              </label>
              <label className="field">
                <span>Email (optional)</span>
                <input
                  type="email"
                  value={form.customerEmail}
                  onChange={(event) => setForm({ ...form, customerEmail: event.target.value })}
                  placeholder="name@example.com"
                />
              </label>
            </div>

            <label className="field">
              <span>Product</span>
              <select
                value={form.productId}
                onChange={(event) => setForm({ ...form, productId: event.target.value })}
                required
              >
                <option value="">Select a product</option>
                {products.map((product) => (
                  <option key={product.id} value={String(product.id)}>
                    {product.sku} · {product.name} · {money(product.price)}
                  </option>
                ))}
              </select>
            </label>

            <div className="form-grid">
              <label className="field">
                <span>Warehouse</span>
                <select
                  value={form.warehouseId}
                  onChange={(event) => setForm({ ...form, warehouseId: event.target.value })}
                  required
                >
                  <option value="">Select a warehouse</option>
                  {warehouses.map((warehouse) => (
                    <option key={warehouse.id} value={String(warehouse.id)}>
                      {warehouse.code} · {warehouse.name}
                    </option>
                  ))}
                </select>
              </label>

              <label className="field">
                <span>Quantity</span>
                <input
                  type="number"
                  min="1"
                  value={form.quantity}
                  onChange={(event) => setForm({ ...form, quantity: event.target.value })}
                  required
                />
              </label>
            </div>

            <div className="order-preview">
              <div>
                <span>Fulfilment</span>
                <strong>
                  {selectedProduct?.name || 'Select a product'}
                  {selectedWarehouse ? ` @ ${selectedWarehouse.code}` : ''}
                </strong>
              </div>
              <div>
                <span>Available now</span>
                <strong className={availableForSelection !== null && availableForSelection < Number(form.quantity || 0) ? 'danger-text' : ''}>
                  {availableForSelection === null ? '—' : `${number(availableForSelection)} units`}
                </strong>
              </div>
              <div>
                <span>Order total</span>
                <strong>{money(estimatedTotal)}</strong>
              </div>
            </div>

            {formError && (
              <div className="form-error"><Icon name="alert" size={16} />{formError}</div>
            )}
          </form>
        </Modal>
      )}
    </div>
  )
}
