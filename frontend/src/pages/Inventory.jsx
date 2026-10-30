import { useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { canManage } from '../auth.js'
import Icon from '../components/Icon.jsx'
import Modal from '../components/Modal.jsx'
import { dateTime, number } from '../format.js'

const EMPTY_ENTRY = {
  productId: '',
  warehouseId: '',
  quantity: '',
  reorderLevel: '10'
}

export default function Inventory({ user, notify }) {
  const [rows, setRows] = useState([])
  const [products, setProducts] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [warehouseFilter, setWarehouseFilter] = useState('all')
  const [lowOnly, setLowOnly] = useState(false)
  const [entryEditor, setEntryEditor] = useState(null)
  const [entryForm, setEntryForm] = useState(EMPTY_ENTRY)
  const [restockTarget, setRestockTarget] = useState(null)
  const [restockQty, setRestockQty] = useState('')
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const manage = canManage(user)

  async function load() {
    setLoading(true)
    setError('')
    try {
      const [inventory, productList, warehouseList] = await Promise.all([
        api('/inventory'),
        api('/products'),
        api('/warehouses')
      ])
      setRows(inventory)
      setProducts(productList)
      setWarehouses(warehouseList)
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

  const filtered = useMemo(() => {
    return rows.filter((row) => {
      if (warehouseFilter !== 'all' && String(row.warehouseId) !== warehouseFilter) return false
      if (lowOnly && !row.lowStock) return false
      return true
    })
  }, [rows, warehouseFilter, lowOnly])

  const totals = useMemo(
    () =>
      rows.reduce(
        (acc, row) => ({
          onHand: acc.onHand + row.quantity,
          reserved: acc.reserved + row.reservedQuantity,
          available: acc.available + row.availableQuantity,
          alerts: acc.alerts + (row.lowStock ? 1 : 0)
        }),
        { onHand: 0, reserved: 0, available: 0, alerts: 0 }
      ),
    [rows]
  )

  function openCreate() {
    setEntryForm({
      ...EMPTY_ENTRY,
      productId: products[0] ? String(products[0].id) : '',
      warehouseId: warehouses[0] ? String(warehouses[0].id) : ''
    })
    setFormError('')
    setEntryEditor({ mode: 'create' })
  }

  function openEdit(row) {
    setEntryForm({
      productId: String(row.productId),
      warehouseId: String(row.warehouseId),
      quantity: String(row.quantity),
      reorderLevel: String(row.reorderLevel)
    })
    setFormError('')
    setEntryEditor({ mode: 'edit', row })
  }

  async function saveEntry(event) {
    event.preventDefault()
    setSaving(true)
    setFormError('')

    const payload = {
      productId: Number(entryForm.productId),
      warehouseId: Number(entryForm.warehouseId),
      quantity: Number(entryForm.quantity),
      reorderLevel: Number(entryForm.reorderLevel)
    }

    try {
      if (entryEditor.mode === 'create') {
        await api('/inventory', { method: 'POST', body: payload })
        notify('Stock record created.')
      } else {
        await api(`/inventory/${entryEditor.row.id}`, { method: 'PUT', body: payload })
        notify('Stock record updated.')
      }
      setEntryEditor(null)
      await load()
    } catch (err) {
      setFormError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function restock(event) {
    event.preventDefault()
    setSaving(true)
    setFormError('')
    try {
      await api(`/inventory/${restockTarget.id}/restock?quantity=${Number(restockQty)}`, {
        method: 'POST'
      })
      notify(`Received ${restockQty} units into ${warehouseMap.get(restockTarget.warehouseId)?.code || 'the warehouse'}.`)
      setRestockTarget(null)
      setRestockQty('')
      await load()
    } catch (err) {
      setFormError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function remove(row) {
    if (!window.confirm('Delete this stock record? This cannot be undone.')) return
    try {
      await api(`/inventory/${row.id}`, { method: 'DELETE' })
      notify('Stock record deleted.')
      await load()
    } catch (err) {
      notify(err.message, 'error')
    }
  }

  return (
    <div className="stack">
      <div className="mini-stats">
        <div><span>On hand</span><strong>{number(totals.onHand)}</strong></div>
        <div><span>Reserved</span><strong>{number(totals.reserved)}</strong></div>
        <div><span>Available</span><strong>{number(totals.available)}</strong></div>
        <div className={totals.alerts ? 'warn' : ''}>
          <span>Low stock</span><strong>{number(totals.alerts)}</strong>
        </div>
      </div>

      <section className="panel">
        <header className="panel-header panel-header-row">
          <div>
            <h2>Stock by warehouse</h2>
            <p>Available = physical quantity − reserved quantity.</p>
          </div>
          <div className="toolbar">
            <select
              className="select"
              value={warehouseFilter}
              onChange={(event) => setWarehouseFilter(event.target.value)}
            >
              <option value="all">All warehouses</option>
              {warehouses.map((warehouse) => (
                <option key={warehouse.id} value={String(warehouse.id)}>
                  {warehouse.code} · {warehouse.name}
                </option>
              ))}
            </select>
            <label className="checkbox filter-check">
              <input
                type="checkbox"
                checked={lowOnly}
                onChange={(event) => setLowOnly(event.target.checked)}
              />
              Low stock only
            </label>
            {manage && (
              <button type="button" className="btn btn-primary" onClick={openCreate}>
                <Icon name="plus" size={16} />
                New stock entry
              </button>
            )}
          </div>
        </header>

        {loading ? (
          <div className="state"><span className="spinner" /><p>Loading stock levels…</p></div>
        ) : error ? (
          <div className="state state-error">
            <Icon name="alert" size={24} />
            <p>{error}</p>
            <button type="button" className="btn btn-secondary" onClick={load}>Retry</button>
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty">
            <Icon name="inventory" size={24} />
            <p>No stock records for the selected filters.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Warehouse</th>
                  <th className="num">On hand</th>
                  <th className="num">Reserved</th>
                  <th className="num">Available</th>
                  <th className="num">Reorder at</th>
                  <th>Status</th>
                  <th>Updated</th>
                  {manage && <th className="right">Actions</th>}
                </tr>
              </thead>
              <tbody>
                {filtered.map((row) => {
                  const product = productMap.get(row.productId)
                  const warehouse = warehouseMap.get(row.warehouseId)
                  return (
                    <tr key={row.id} className={row.lowStock ? 'row-warn' : ''}>
                      <td>
                        <div className="cell-title">{product?.name || `Product #${row.productId}`}</div>
                        <div className="cell-sub">{product?.sku || '—'}</div>
                      </td>
                      <td>
                        <div className="cell-title">{warehouse?.code || `WH-${row.warehouseId}`}</div>
                        <div className="cell-sub">{warehouse?.city || '—'}</div>
                      </td>
                      <td className="num">{number(row.quantity)}</td>
                      <td className="num">{number(row.reservedQuantity)}</td>
                      <td className="num">
                        <strong>{number(row.availableQuantity)}</strong>
                      </td>
                      <td className="num">{number(row.reorderLevel)}</td>
                      <td>
                        <span className={`badge ${row.lowStock ? 'badge-rejected' : 'badge-active'}`}>
                          {row.lowStock ? 'LOW STOCK' : 'HEALTHY'}
                        </span>
                      </td>
                      <td className="muted">{dateTime(row.updatedAt)}</td>
                      {manage && (
                        <td className="right">
                          <div className="row-actions">
                            <button
                              type="button"
                              className="btn btn-tiny"
                              onClick={() => {
                                setRestockTarget(row)
                                setRestockQty('10')
                                setFormError('')
                              }}
                            >
                              Restock
                            </button>
                            <button
                              type="button"
                              className="icon-btn"
                              title="Edit stock record"
                              onClick={() => openEdit(row)}
                            >
                              <Icon name="edit" size={16} />
                            </button>
                            <button
                              type="button"
                              className="icon-btn danger"
                              title="Delete stock record"
                              onClick={() => remove(row)}
                            >
                              <Icon name="trash" size={16} />
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {entryEditor && (
        <Modal
          title={entryEditor.mode === 'create' ? 'New stock entry' : 'Edit stock record'}
          subtitle="The inventory service validates the product against the catalog."
          onClose={() => setEntryEditor(null)}
          footer={
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setEntryEditor(null)}>
                Cancel
              </button>
              <button type="submit" form="stock-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save stock record'}
              </button>
            </>
          }
        >
          <form id="stock-form" className="form" onSubmit={saveEntry}>
            <label className="field">
              <span>Product</span>
              <select
                value={entryForm.productId}
                onChange={(event) => setEntryForm({ ...entryForm, productId: event.target.value })}
                required
              >
                <option value="">Select a product</option>
                {products.map((product) => (
                  <option key={product.id} value={String(product.id)}>
                    {product.sku} · {product.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>Warehouse</span>
              <select
                value={entryForm.warehouseId}
                onChange={(event) => setEntryForm({ ...entryForm, warehouseId: event.target.value })}
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

            <div className="form-grid">
              <label className="field">
                <span>Physical quantity</span>
                <input
                  type="number"
                  min="0"
                  value={entryForm.quantity}
                  onChange={(event) => setEntryForm({ ...entryForm, quantity: event.target.value })}
                  required
                />
              </label>
              <label className="field">
                <span>Reorder level</span>
                <input
                  type="number"
                  min="0"
                  value={entryForm.reorderLevel}
                  onChange={(event) => setEntryForm({ ...entryForm, reorderLevel: event.target.value })}
                  required
                />
              </label>
            </div>

            <p className="hint">
              Creating a stock entry for an existing product/warehouse pair returns a conflict.
            </p>

            {formError && (
              <div className="form-error"><Icon name="alert" size={16} />{formError}</div>
            )}
          </form>
        </Modal>
      )}

      {restockTarget && (
        <Modal
          title="Receive stock"
          subtitle={`${productMap.get(restockTarget.productId)?.name || 'Product'} at ${warehouseMap.get(restockTarget.warehouseId)?.code || 'warehouse'}`}
          onClose={() => setRestockTarget(null)}
          footer={
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setRestockTarget(null)}>
                Cancel
              </button>
              <button type="submit" form="restock-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Receiving…' : 'Receive stock'}
              </button>
            </>
          }
        >
          <form id="restock-form" className="form" onSubmit={restock}>
            <div className="stock-summary">
              <div><span>On hand</span><strong>{number(restockTarget.quantity)}</strong></div>
              <div><span>Reserved</span><strong>{number(restockTarget.reservedQuantity)}</strong></div>
              <div><span>Available</span><strong>{number(restockTarget.availableQuantity)}</strong></div>
            </div>
            <label className="field">
              <span>Units received</span>
              <input
                type="number"
                min="1"
                value={restockQty}
                onChange={(event) => setRestockQty(event.target.value)}
                required
                autoFocus
              />
            </label>
            {formError && (
              <div className="form-error"><Icon name="alert" size={16} />{formError}</div>
            )}
          </form>
        </Modal>
      )}
    </div>
  )
}
