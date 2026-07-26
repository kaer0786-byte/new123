// Mock 数据层 — 与 docs/PRD.md §6 接口契约一一对应。
// 联调真实后端时,将环境变量 VITE_USE_MOCK 设为 'false' 即可整体切换(见 index.js)。

const delay = (ms = 300) => new Promise(r => setTimeout(r, ms + Math.random() * 300))

const PRODUCT_STATUS = ['draft', 'pending', 'published', 'failed']
const FAIL_REASONS = ['找不到元素', '验证码拦截', '页面加载超时', '千牛闪退', '图片上传失败']

const svgPlaceholder = (text, bg) =>
  `data:image/svg+xml,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="${bg}"/><text x="100" y="105" font-size="20" text-anchor="middle" fill="#fff" font-family="sans-serif">${text}</text></svg>`
  )}`

const screenshotPlaceholder = errText =>
  `data:image/svg+xml,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="360" height="720"><rect width="360" height="720" fill="#1f2329"/><rect x="20" y="80" width="320" height="48" rx="6" fill="#3a3f47"/><rect x="20" y="150" width="320" height="360" rx="6" fill="#2b3037"/><rect x="40" y="540" width="280" height="60" rx="8" fill="#e34948" opacity="0.85"/><text x="180" y="576" font-size="16" text-anchor="middle" fill="#fff" font-family="sans-serif">${errText}</text></svg>`
  )}`

// ---------- 种子数据 ----------
const NAMES = [
  '夏季冰丝短袖T恤男潮流宽松半袖', '简约帆布单肩包大容量学生书包', '无线蓝牙耳机降噪入耳式长续航',
  '家用不锈钢保温杯大容量便携水杯', '韩版宽松牛仔裤女高腰显瘦直筒', '车载手机支架磁吸出风口通用',
  '儿童卡通雨伞自动折叠晴雨两用', '北欧风陶瓷马克杯情侣咖啡杯', '运动速干短裤男夏季薄款透气',
  '少女心化妆包大容量便携收纳包', 'USB小风扇迷你静音桌面办公', '纯棉白色袜子男女中筒袜四季'
]
const COLORS = ['#7c9fd6', '#d6a97c', '#8fbf8f', '#c98ab8', '#8ab8c9', '#b0a0d0']

function seededProducts() {
  const list = []
  for (let i = 0; i < 34; i++) {
    const name = NAMES[i % NAMES.length]
    const status = PRODUCT_STATUS[i % 4 === 3 ? 3 : i % 3]
    const cost = +(5 + (i * 3.7) % 60).toFixed(2)
    list.push({
      id: 1000 + i,
      title: name,
      optimizedTitles: [`${name} 2026新款`, `${name} 爆款推荐`, `${name} 高品质`],
      selectedTitle: `${name} 2026新款`,
      costPrice: cost,
      price: +(cost * 1.3).toFixed(2),
      status,
      image: svgPlaceholder('商品' + (i + 1), COLORS[i % COLORS.length]),
      detailImages: [0, 1, 2].map(n => svgPlaceholder(`详情${n + 1}`, COLORS[(i + n) % COLORS.length])),
      skus: [
        { spec: 'S / 白色', price: +(cost * 1.3).toFixed(2), stock: 100 },
        { spec: 'M / 白色', price: +(cost * 1.3).toFixed(2), stock: 120 },
        { spec: 'L / 黑色', price: +(cost * 1.35).toFixed(2), stock: 80 }
      ],
      sourceUrl: `https://detail.1688.com/offer/${661000000 + i}.html`,
      collectedAt: new Date(Date.now() - i * 5.3 * 3600e3).toISOString()
    })
  }
  return list
}

function seededTasks(products) {
  const list = []
  for (let i = 0; i < 42; i++) {
    const p = products[i % products.length]
    const type = i % 2 === 0 ? 'collect' : 'publish'
    const failed = i % 5 === 0
    const running = i === 1
    const status = running ? 'running' : failed ? 'failed' : 'success'
    const reason = FAIL_REASONS[i % FAIL_REASONS.length]
    const start = new Date(Date.now() - i * 3.1 * 3600e3)
    const steps = type === 'collect'
      ? [['检测1688页面', true], ['等待页面稳定', true], ['提取标题价格', !failed], ['提取SKU与图片', !failed && !running]]
      : [['打开千牛', true], ['进入发布页', true], ['填写标题', !failed], ['上传图片', !failed && !running], ['保存草稿', !failed && !running]]
    list.push({
      id: 2000 + i,
      type,
      status,
      productId: p.id,
      productTitle: p.title,
      productImage: p.image,
      startedAt: start.toISOString(),
      durationSec: 20 + (i * 13) % 160,
      failReason: failed ? reason : null,
      screenshot: failed ? screenshotPlaceholder(reason) : null,
      steps: steps.map(([name, ok], idx) => {
        const isFailedStep = failed && idx === steps.findIndex(s => !s[1])
        return {
          name,
          status: isFailedStep ? 'failed' : ok ? 'success' : 'skipped',
          at: new Date(start.getTime() + idx * 4000).toISOString(),
          detail: isFailedStep ? `${reason}: ${type === 'publish' ? 'title_input' : 'sku_panel'}` : ''
        }
      })
    })
  }
  return list
}

const db = {
  products: seededProducts(),
  members: [
    { id: 1, username: 'admin', role: 'admin', createdAt: '2026-06-01T09:00:00Z' },
    { id: 2, username: 'op01', role: 'operator', createdAt: '2026-06-15T09:00:00Z' }
  ],
  settings: {
    markupType: 'PERCENT', markupValue: 30,
    aiModel: 'tongyi', aiApiKey: 'sk-mock-key-demo-8f3a',
    shippingTemplate: '默认运费模板', deliveryPlace: '浙江杭州', autoSubmit: false,
    minDelayMs: 300, maxDelayMs: 1500
  }
}
db.tasks = seededTasks(db.products)
db.mappings = db.products
  .filter(p => p.status === 'published')
  .map((p, i) => ({
    id: 3000 + i,
    sourceTitle: p.title, sourceId: String(661000000 + p.id - 1000), sourceUrl: p.sourceUrl, sourceImage: p.image,
    taobaoTitle: p.selectedTitle, taobaoId: String(778000000 + i),
    markupSnapshot: '百分比加价 30%',
    publishedAt: new Date(Date.now() - i * 8 * 3600e3).toISOString(),
    skuMap: p.skus.map(s => ({ source: s.spec, taobao: s.spec, price: s.price })),
    raw: { title: p.title, price: p.costPrice, skus: p.skus }
  }))

const USERS = { admin: { password: 'admin123', role: 'admin' }, op01: { password: 'op123', role: 'operator' } }

function paginate(list, page = 1, pageSize = 20) {
  const total = list.length
  const start = (page - 1) * pageSize
  return { total, page, pageSize, items: list.slice(start, start + pageSize) }
}

// ---------- 接口实现 ----------
export const mockApi = {
  async login({ username, password }) {
    await delay()
    const u = USERS[username]
    if (!u || u.password !== password) throw new Error('账号或密码错误')
    return { token: 'mock-jwt-' + username, username, role: u.role }
  },

  async dashboardSummary() {
    await delay()
    const today = db.tasks.filter(t => new Date(t.startedAt).toDateString() === new Date().toDateString())
    const pub = db.tasks.filter(t => t.type === 'publish')
    const ok = pub.filter(t => t.status === 'success').length
    return {
      todayCollected: Math.max(today.filter(t => t.type === 'collect').length, 6),
      todayPublished: Math.max(today.filter(t => t.type === 'publish').length, 5),
      successRate: pub.length ? Math.round((ok / pub.length) * 1000) / 10 : 0,
      pendingFailures: db.tasks.filter(t => t.status === 'failed').length
    }
  },

  async dashboardTrend({ days = 7 }) {
    await delay()
    const out = []
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(Date.now() - i * 864e5)
      out.push({
        date: `${d.getMonth() + 1}/${d.getDate()}`,
        collected: 4 + Math.round(8 * Math.abs(Math.sin(d.getDate() * 1.7))),
        published: 3 + Math.round(7 * Math.abs(Math.sin(d.getDate() * 2.3)))
      })
    }
    return out
  },

  async dashboardFailures() {
    await delay()
    const counts = {}
    db.tasks.filter(t => t.status === 'failed').forEach(t => {
      counts[t.failReason] = (counts[t.failReason] || 0) + 1
    })
    return Object.entries(counts).map(([reason, count]) => ({ reason, count }))
      .sort((a, b) => b.count - a.count)
  },

  async listProducts({ status = '', keyword = '', page = 1 }) {
    await delay()
    let list = db.products
    if (status) list = list.filter(p => p.status === status)
    if (keyword) list = list.filter(p => p.title.includes(keyword) || p.selectedTitle.includes(keyword))
    return paginate(list, page)
  },

  async getProduct(id) {
    await delay(150)
    const p = db.products.find(p => p.id === +id)
    if (!p) throw new Error('商品不存在')
    return JSON.parse(JSON.stringify(p))
  },

  async updateProduct(id, patch) {
    await delay()
    const p = db.products.find(p => p.id === +id)
    if (!p) throw new Error('商品不存在')
    Object.assign(p, patch)
    return { ok: true }
  },

  async deleteProducts(ids) {
    await delay()
    db.products = db.products.filter(p => !ids.includes(p.id))
    return { ok: true }
  },

  async listMappings({ keyword = '', page = 1 }) {
    await delay()
    let list = db.mappings
    if (keyword) list = list.filter(m => m.sourceTitle.includes(keyword) || m.taobaoTitle.includes(keyword) || m.taobaoId.includes(keyword))
    return paginate(list, page)
  },

  async getMapping(id) {
    await delay(150)
    const m = db.mappings.find(m => m.id === +id)
    if (!m) throw new Error('映射不存在')
    return m
  },

  async deleteMapping(id) {
    await delay()
    db.mappings = db.mappings.filter(m => m.id !== +id)
    return { ok: true }
  },

  async listTasks({ type = '', status = '', page = 1 }) {
    await delay()
    let list = db.tasks
    if (type) list = list.filter(t => t.type === type)
    if (status) list = list.filter(t => t.status === status)
    return paginate(list, page)
  },

  async getTask(id) {
    await delay(150)
    const t = db.tasks.find(t => t.id === +id)
    if (!t) throw new Error('任务不存在')
    return t
  },

  async retryTask(id) {
    await delay()
    const t = db.tasks.find(t => t.id === +id)
    if (!t) throw new Error('任务不存在')
    t.status = 'running'
    return { ok: true, message: '重试指令已下发至 App' }
  },

  async getSettings() {
    await delay(150)
    return { ...db.settings, aiApiKey: maskKey(db.settings.aiApiKey) }
  },

  async updateSettings(patch) {
    await delay()
    // 未修改的脱敏 Key 不覆盖原值
    if (patch.aiApiKey && patch.aiApiKey.includes('****')) delete patch.aiApiKey
    Object.assign(db.settings, patch)
    return { ok: true }
  },

  async listMembers() {
    await delay(150)
    return db.members
  },

  async createMember({ username, password, role }) {
    await delay()
    if (db.members.some(m => m.username === username)) throw new Error('账号已存在')
    const m = { id: Date.now(), username, role, createdAt: new Date().toISOString() }
    db.members.push(m)
    USERS[username] = { password, role }
    return m
  },

  async deleteMember(id) {
    await delay()
    db.members = db.members.filter(m => m.id !== id)
    return { ok: true }
  }
}

function maskKey(key) {
  if (!key || key.length <= 4) return key
  return '****' + key.slice(-4)
}
