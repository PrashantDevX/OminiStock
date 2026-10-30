import { useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { canManage } from '../auth.js'
import Icon from '../components/Icon.jsx'
import Modal from '../components/Modal.jsx'
import { money } from '../format.js'

const EMPTY_FORM = {
  sku: '',
  name: '',
  category: '',
  price: '',
  description: '',
  active: true
}

export default function Products({ user, notify }) {
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [editor, setEditor] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const manage = canManage(user)

  async function load() {
    setLoading(true)
    setError('')
    try {
      setProducts(await api('/products'))
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    if (!term) return products
    return products.filter((product) =>
      [product.sku, product.name, product.category]
        .join(' ')
        .toLowerCase()
        .includes(term)
    )
  }, [products, search])

  function openCreate() {
    setForm(EMPTY_FORM)
    setFormError('')
    setEditor({ mode: 'create' })
  }

  function openEdit(product) {
    setForm({
      sku: product.sku,
      name: product.name,
      category: product.category,
      price: String(product.price),
      description: product.description || '',
      active: product.active
    })
    setFormError('')
    setEditor({ mode: 'edit', product })
  }

  async function save(event) {
    event.preventDefault()
    setSaving(true)
    setFormError('')

    const payload = {
      sku: form.sku,
      name: form.name,
      category: form.category,
      price: Number(form.price),
      description: form.description,
      active: form.active
    }

    try {
      if (editor.mode === 'create') {
        await api('/products', { method: 'POST', body: payload })
        notify('Product added to the catalog.')
      } else {
        await api(`/products/${editor.product.id}`, { method: 'PUT', body: payload })
        notify('Product updated.')
      }
      setEditor(null)
      await load()
    } catch (err) {
      setFormError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function remove(product) {
    if (!window.confirm(`Delete ${product.name} (${product.sku})? Inventory rows may still reference it.`)) {
      return
    }
    try {
      await api(`/products/${product.id}`, { method: 'DELETE' })
      notify('Product deleted.')
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
            <h2>Catalog entries</h2>
            <p>{products.length} products maintained by the product service.</p>
          </div>
          <div className="toolbar">
            <label className="search">
              <Icon name="search" size={16} />
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search SKU, name or category"
              />
            </label>
            {manage && (
              <button type="button" className="btn btn-primary" onClick={openCreate}>
                <Icon name="plus" size={16} />
                Add product
              </button>
            )}
          </div>
        </header>

        {!manage && (
          <div className="notice">
            <Icon name="info" size={16} />
            Your STAFF role can browse the catalog. Managers and admins can edit it.
          </div>
        )}

        {loading ? (
          <div className="state"><span className="spinner" /><p>Loading catalog…</p></div>
        ) : error ? (
          <div className="state state-error">
            <Icon name="alert" size={24} />
            <p>{error}</p>
            <button type="button" className="btn btn-secondary" onClick={load}>Retry</button>
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty">
            <Icon name="products" size={24} />
            <p>No products match “{search}”.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Product</th>
                  <th>Category</th>
                  <th className="num">Unit price</th>
                  <th>Status</th>
                  {manage && <th className="right">Actions</th>}
                </tr>
              </thead>
              <tbody>
                {filtered.map((product) => (
                  <tr key={product.id}>
                    <td className="mono">{product.sku}</td>
                    <td>
                      <div className="cell-title">{product.name}</div>
                      <div className="cell-sub">{product.description || '—'}</div>
                    </td>
                    <td><span className="tag">{product.category}</span></td>
                    <td className="num">{money(product.price)}</td>
                    <td>
                      <span className={`badge ${product.active ? 'badge-active' : 'badge-inactive'}`}>
                        {product.active ? 'ACTIVE' : 'INACTIVE'}
                      </span>
                    </td>
                    {manage && (
                      <td className="right">
                        <div className="row-actions">
                          <button
                            type="button"
                            className="icon-btn"
                            title="Edit product"
                            onClick={() => openEdit(product)}
                          >
                            <Icon name="edit" size={16} />
                          </button>
                          <button
                            type="button"
                            className="icon-btn danger"
                            title="Delete product"
                            onClick={() => remove(product)}
                          >
                            <Icon name="trash" size={16} />
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {editor && (
        <Modal
          title={editor.mode === 'create' ? 'Add product' : 'Edit product'}
          subtitle="Catalog changes are published through the product service."
          onClose={() => setEditor(null)}
          footer={
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setEditor(null)}>
                Cancel
              </button>
              <button type="submit" form="product-form" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : editor.mode === 'create' ? 'Create product' : 'Save changes'}
              </button>
            </>
          }
        >
          <form id="product-form" className="form" onSubmit={save}>
            <div className="form-grid">
              <label className="field">
                <span>SKU</span>
                <input
                  value={form.sku}
                  onChange={(event) => setForm({ ...form, sku: event.target.value })}
                  placeholder="SKU"
                  required
                />
              </label>
              <label className="field">
                <span>Category</span>
                <input
                  value={form.category}
                  onChange={(event) => setForm({ ...form, category: event.target.value })}
                  placeholder="Category"
                  required
                />
              </label>
            </div>

            <label className="field">
              <span>Product name</span>
              <input
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                placeholder="Product name"
                required
              />
            </label>

            <div className="form-grid">
              <label className="field">
                <span>Unit price (INR)</span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.price}
                  onChange={(event) => setForm({ ...form, price: event.target.value })}
                  placeholder="0.00"
                  required
                />
              </label>
              <label className="field checkbox-field">
                <span>Availability</span>
                <label className="checkbox">
                  <input
                    type="checkbox"
                    checked={form.active}
                    onChange={(event) => setForm({ ...form, active: event.target.checked })}
                  />
                  Active in catalog
                </label>
              </label>
            </div>

            <label className="field">
              <span>Description</span>
              <textarea
                rows="3"
                value={form.description}
                onChange={(event) => setForm({ ...form, description: event.target.value })}
                placeholder="Optional description"
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
