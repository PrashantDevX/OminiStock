const TOKEN_KEY = 'omnistock_token'
const USER_KEY = 'omnistock_user'

export function saveSession(auth) {
  localStorage.setItem(TOKEN_KEY, auth.token)
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getSession() {
  const token = localStorage.getItem(TOKEN_KEY)
  const rawUser = localStorage.getItem(USER_KEY)
  if (!token || !rawUser) return null
  try {
    return { token, user: JSON.parse(rawUser) }
  } catch {
    clearSession()
    return null
  }
}

export function canManage(user) {
  return user?.role === 'ADMIN' || user?.role === 'MANAGER'
}

export function isAdmin(user) {
  return user?.role === 'ADMIN'
}
