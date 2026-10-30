import { useState } from 'react'
import { api } from '../api.js'
import Icon from '../components/Icon.jsx'

export default function Login({ onLogin, notify }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({
    username: '',
    password: '',
    fullName: ''
  })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const update = (field) => (event) =>
    setForm((current) => ({ ...current, [field]: event.target.value }))

  async function submit(event) {
    event.preventDefault()
    setError('')
    setBusy(true)

    try {
      if (mode === 'login') {
        const auth = await api('/auth/login', {
          method: 'POST',
          auth: false,
          body: { username: form.username, password: form.password }
        })
        onLogin(auth)
      } else {
        const auth = await api('/auth/register', {
          method: 'POST',
          auth: false,
          body: {
            username: form.username,
            password: form.password,
            fullName: form.fullName
          }
        })
        notify('Staff account created. You are now signed in.', 'success')
        onLogin(auth)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-shell">
      <section className="login-panel">
        <div className="login-card">
          <div className="login-heading">
            <div className="brand-mark">OS</div>
            <span className="eyebrow">OmniStock</span>
            <h2>{mode === 'login' ? 'Sign in' : 'Create an account'}</h2>
            <p>
              {mode === 'login'
                ? 'Sign in to manage products, stock and orders.'
                : 'Self-registration always creates a read-only STAFF account.'}
            </p>
          </div>

          <div className="segmented">
            <button
              type="button"
              className={mode === 'login' ? 'active' : ''}
              onClick={() => { setMode('login'); setError('') }}
            >
              Sign in
            </button>
            <button
              type="button"
              className={mode === 'register' ? 'active' : ''}
              onClick={() => { setMode('register'); setError('') }}
            >
              Register
            </button>
          </div>

          <form onSubmit={submit} className="form">
            {mode === 'register' && (
              <label className="field">
                <span>Full name</span>
                <input
                  value={form.fullName}
                  onChange={update('fullName')}
                  placeholder="Full name"
                  required
                  autoComplete="name"
                />
              </label>
            )}

            <label className="field">
              <span>Username</span>
              <input
                value={form.username}
                onChange={update('username')}
                placeholder="Username"
                required
                autoComplete="username"
              />
            </label>

            <label className="field">
              <span>Password</span>
              <input
                type="password"
                value={form.password}
                onChange={update('password')}
                placeholder="••••••••"
                required
                minLength={mode === 'register' ? 6 : undefined}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              />
            </label>

            {error && (
              <div className="form-error">
                <Icon name="alert" size={16} />
                <span>{error}</span>
              </div>
            )}

            <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
              {busy
                ? 'Please wait…'
                : mode === 'login'
                  ? 'Sign in securely'
                  : 'Create account'}
            </button>
          </form>

        </div>
      </section>
    </div>
  )
}
