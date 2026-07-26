// API 层:默认走 Mock;联调真实后端时设 VITE_USE_MOCK=false,
// 并保证后端实现 docs/PRD.md §6 的接口契约。
import { mockApi } from './mock'
import { auth } from '@/store/auth'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'
const BASE = import.meta.env.VITE_API_BASE || '/api'

async function http(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(auth.token ? { Authorization: `Bearer ${auth.token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  if (res.status === 401) {
    auth.logout('expired')
    throw new Error('登录已过期')
  }
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text || `请求失败(${res.status})`)
  }
  return res.json()
}

const qs = params => {
  const s = new URLSearchParams(
    Object.entries(params || {}).filter(([, v]) => v !== '' && v != null)
  ).toString()
  return s ? `?${s}` : ''
}

const realApi = {
  login: body => http('POST', '/auth/login', body),
  dashboardSummary: () => http('GET', '/dashboard/summary'),
  dashboardTrend: p => http('GET', `/dashboard/trend${qs(p)}`),
  dashboardFailures: () => http('GET', '/dashboard/failures?days=30'),
  listProducts: p => http('GET', `/products${qs(p)}`),
  getProduct: id => http('GET', `/products/${id}`),
  updateProduct: (id, body) => http('PUT', `/products/${id}`, body),
  deleteProducts: ids => http('DELETE', '/products', { ids }),
  listMappings: p => http('GET', `/mappings${qs(p)}`),
  getMapping: id => http('GET', `/mappings/${id}`),
  deleteMapping: id => http('DELETE', `/mappings/${id}`),
  listTasks: p => http('GET', `/tasks${qs(p)}`),
  getTask: id => http('GET', `/tasks/${id}`),
  retryTask: id => http('POST', `/tasks/${id}/retry`),
  getSettings: () => http('GET', '/settings'),
  updateSettings: body => http('PUT', '/settings', body),
  listMembers: () => http('GET', '/members'),
  createMember: body => http('POST', '/members', body),
  deleteMember: id => http('DELETE', `/members/${id}`)
}

export const api = USE_MOCK ? mockApi : realApi
export const usingMock = USE_MOCK
