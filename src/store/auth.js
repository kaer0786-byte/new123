import { reactive } from 'vue'

const saved = JSON.parse(localStorage.getItem('auth') || 'null')

export const auth = reactive({
  token: saved?.token || '',
  username: saved?.username || '',
  role: saved?.role || '',

  get isAdmin() { return this.role === 'admin' },
  get loggedIn() { return !!this.token },

  login({ token, username, role }) {
    this.token = token
    this.username = username
    this.role = role
    localStorage.setItem('auth', JSON.stringify({ token, username, role }))
  },

  logout(reason) {
    this.token = ''
    this.username = ''
    this.role = ''
    localStorage.removeItem('auth')
    const q = reason === 'expired' ? '?expired=1' : ''
    // 避免循环依赖,直接跳转
    if (!location.hash.startsWith('#/login')) location.hash = '#/login' + q
  }
})
