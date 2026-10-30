import { useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { canManage } from '../auth.js'
import Icon from '../components/Icon.jsx'
import Modal from '../components/Modal.jsx'
import { number } from '../format.js'

const EMPTY_FORM = { code: '', name: '', city: '' }

export default function Warehouses({ user, notify }) {
  const [warehouses, setWarehouses] = useState([])
  const [inventory, setInventory] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editor, setEditor] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const manage = canManage(user)

  async function load() {
    setLoading(true)
    setError('')
    try {
      const [warehouseList, stock] = await Promise.all([
        api('/warehouses'),
        api('/inventory')
      ])
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

  const stats = useMemo(() => {
    const map = new Map()
    for (const row of inventory) {
      const current = map.get(row.warehouseId) || {
        skus: 0,
        available: 0,
        alerts: 0
      }
      current.skus += 1
      current.available += row.availableQuantity
      current.alerts += row.lowStock ? 1 : 0
      map.set(row.warehouseId, current)
    }
    return map
  }, [inventory])

  function openCreate() {
    setForm(EMPTY_FORM)
    setFormError('')
    setEditor({ mode: 'create' })
  }

  function openEdit(warehouse) {
    setForm({ code: warehouse.code, name: warehouse.name, city: warehouse.city || '' })
    setFormError('')
    setEditor({ mode: 'edit', warehouse })
  }

  async function save(event) {
    event.preventDefault()
    setSaving(true)
    setFormError('')
    try {
      if (editor.mode === 'create') {
        await api('/warehouses', { method: 'POST', body: form })
        notify('Warehouse added to the network.')
      } else {
        await api(`/warehouses/${editor.warehouse.id}`, { method: 'PUT', body: form })
        notify('Warehouse updated.')
      }
      setEditor(null)
      await load()
    } catch (err) {
      setFormError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function remove(warehouse) {
    if (!window.confirm(`Remove ${warehouse.code}? Warehouses holding stock cannot be deleted.`)) {
      return
    }
    try {
      await api(`/warehouses/${warehouse.id}`, { method: 'DELETE' })
      notify('Warehouse removed.')
      await load()
    } catch (err) {
      notify(err.message, 'error')
    }
  }

  return (
    <div className="stack">
      <section className="panel">
        <header className="panel-header panel-header-row">
          <div>
            <h2>Regional distribution hubs</h2>
            <p>Warehouses are owned by the inventory service.</p>
          </div>
          {manage && (
            <button type="button" className="btn btn-primary" onClick={openCreate}>
              <Icon name="plus" size={16} />
              Add warehouse
            </button>
          )}
        </header>

        {loading ? (
          <div className="state"><span className="spinner" /><p>Loading warehouses…</p></div>
        ) : error ? (
          <div className="state state-error">
            <Icon name="alert" size={24} />
            <p>{error}</p>
            <button type="button" className="btn btn-secondary" onClick={load}>Retry</button>
          </div>
        ) : (
          <div className="warehouse-grid">
            {warehouses.map((warehouse) => {
              const summary = stats.get(warehouse.id) || { skus: 0, available: 0, alerts: 0 }
              return (
                <article key={warehouse.id} className="warehouse-card">
                  <header>
                    <div>
                      <span className="warehouse-code">{warehouse.code}</span>
                      <h3>{warehouse.name}</h3>
                      <p>{warehouse.city || 'Location not set'}</p>
                    </div>
                    {manage && (
                      <div className="row-actions">
                        <button
                          type="button"
                          className="icon-btn"
                          title="Edit warehouse"
                          onClick={() => openEdit(warehouse)}
                        >
                          <Icon name="edit" size={16} />
                        </button>
                        <button
                          type="button"
                          className="icon-btn danger"
                          title="Delete warehouse"
                          onClick={() => remove(warehouse)}
                        >
                          <Icon name="trash" size={16} />
                        </button>
                      </div>
                    )}
                  </header>

                  <dl className="warehouse-stats">
                    <div><dt>SKUs stocked</dt><dd>{number(summary.skus)}</dd></div>
                    <div><dt>Available units</dt><dd>{number(summary.available)}</dd></div>
                    <div><dt>Low stock</dt><dd className={summary.alerts ? 'danger-text' : ''}>{number(summary.alerts)}</dd></div>
                  </dl>
                </article>
              )
            })}
          </div>
        )}
      </section>

      {editor && (
        <Modal
          title={editor.mode === 'create' ? 'Add warehouse' : 'Edit warehouse'}
          subtitle="The code must be unique across the network."
          onClose={() => setEditor(null)}
          footer={
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setEditor(null)}>
                Cancel
              </button>
              <button type="submit" form="warehouse-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save warehouse'}
              </button>
            </>
          }
        >
          <form id="warehouse-form" className="form" onSubmit={save}>
            <label className="field">
              <span>Warehouse code</span>
              <input
                value={form.code}
                onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })}
                placeholder="Warehouse code"
                required
              />
            </label>
            <label className="field">
              <span>Name</span>
              <input
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                placeholder="Warehouse name"
                required
              />
            </label>
            <label className="field">
              <span>City</span>
              <input
                value={form.city}
                onChange={(event) => setForm({ ...form, city: event.target.value })}
                placeholder="City"
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
