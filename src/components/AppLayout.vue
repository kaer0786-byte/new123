<template>
  <div class="layout">
    <aside class="sidebar" :class="{ narrow }">
      <div class="brand">
        <span class="logo">⚡</span>
        <span v-if="!narrow" class="brand-name">自动化助手后台</span>
      </div>
      <nav>
        <router-link
          v-for="item in navItems" :key="item.path"
          :to="item.path" class="nav-item"
          :class="{ active: isActive(item.path) }"
          :title="item.label"
        >
          <span class="icon" aria-hidden="true">{{ item.icon }}</span>
          <span v-if="!narrow">{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>

    <div class="main">
      <header class="topbar">
        <h1>{{ route.meta.title || '' }}</h1>
        <div class="user">
          <span v-if="usingMock" class="tag tag-blue" title="当前为模拟数据,联调时设 VITE_USE_MOCK=false">Mock 数据</span>
          <span class="caption">{{ auth.username }}({{ auth.isAdmin ? '管理员' : '操作员' }})</span>
          <button class="link-btn" @click="logout">登出</button>
        </div>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/store/auth'
import { usingMock } from '@/api'

const route = useRoute()
const router = useRouter()

const navItems = computed(() => {
  const items = [
    { path: '/dashboard', label: '数据看板', icon: '📊' },
    { path: '/products', label: '商品库', icon: '📦' },
    { path: '/mappings', label: '映射管理', icon: '🔗' },
    { path: '/tasks', label: '任务日志', icon: '📋' }
  ]
  if (auth.isAdmin) {
    items.push(
      { path: '/settings', label: '配置中心', icon: '⚙️' },
      { path: '/members', label: '成员管理', icon: '👥' }
    )
  }
  return items
})

const isActive = path => route.path === path || (path !== '/dashboard' && route.path.startsWith(path))

// 1024–1279px 侧边栏收窄(DESIGN_SPEC §6)
const narrow = ref(false)
const onResize = () => { narrow.value = window.innerWidth < 1280 }
onMounted(() => { onResize(); window.addEventListener('resize', onResize) })
onUnmounted(() => window.removeEventListener('resize', onResize))

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { display: flex; height: 100%; }
.sidebar {
  width: 220px; flex: none; background: var(--bg-card);
  border-right: 1px solid var(--border);
  display: flex; flex-direction: column;
}
.sidebar.narrow { width: 64px; }
.brand {
  display: flex; align-items: center; gap: 8px;
  height: 56px; padding: 0 16px; border-bottom: 1px solid var(--border);
}
.logo { font-size: 20px; }
.brand-name { font-weight: 600; white-space: nowrap; }
nav { padding: 8px; display: flex; flex-direction: column; gap: 2px; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  height: 40px; padding: 0 12px; border-radius: var(--radius-ctl);
  color: var(--text-primary); position: relative; white-space: nowrap;
}
.narrow .nav-item { justify-content: center; padding: 0; }
.nav-item:hover { background: var(--bg-page); color: var(--text-primary); }
.nav-item.active { background: var(--primary-bg); color: var(--primary); }
.nav-item.active::before {
  content: ''; position: absolute; left: 0; top: 8px; bottom: 8px;
  width: 3px; border-radius: 2px; background: var(--primary);
}
.icon { font-size: 16px; }

.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar {
  height: 56px; flex: none; background: var(--bg-card);
  border-bottom: 1px solid var(--border);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px;
}
.user { display: flex; align-items: center; gap: 12px; }
.content { flex: 1; overflow: auto; padding: 24px; }
</style>
