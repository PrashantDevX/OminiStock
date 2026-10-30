const API_BASE = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '')

let unauthorizedHandler = () => {}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

function parse(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

/**
 * Small fetch wrapper. Adds the JWT, normalises API errors and
 * redirects to the login screen when the token expires.
 */
export async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const token = localStorage.getItem('omnistock_token')
  if (auth && token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch {
    throw new Error('Cannot reach the API Gateway. Start the backend and try again.')
  }

  if (response.status === 401) {
    unauthorizedHandler()
    throw new Error('Your session has expired. Please sign in again.')
  }

  const data = parse(await response.text())

  if (!response.ok) {
    const message =
      (data && (data.message || data.error)) || `Request failed with status ${response.status}`
    const error = new Error(message)
    error.status = response.status
    error.data = data
    throw error
  }

  return data
}

export const gatewayUrl = API_BASE
