<template>
  <div class="login-page">
    <form class="card login-card" @submit.prevent="submit">
      <div class="head">
        <span class="logo">⚡</span>
        <h1>电商自动化助手 · 管理后台</h1>
      </div>

      <div v-if="banner" class="banner">{{ banner }}</div>

      <div class="field">
        <label for="u">账号</label>
        <input id="u" v-model.trim="username" class="input" autocomplete="username" placeholder="请输入账号" />
      </div>
      <div class="field">
        <label for="p">密码</label>
        <input id="p" v-model="password" class="input" type="password" autocomplete="current-password" placeholder="请输入密码" />
      </div>

      <button class="btn btn-primary full" :disabled="busy || !username || !password">
        <span v-if="busy" class="spinner"></span>登 录
      </button>

      <p v-if="usingMock" class="caption hint">演示账号:admin / admin123(管理员)· op01 / op123(操作员)</p>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, usingMock } from '@/api'
import { auth } from '@/store/auth'

const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const busy = ref(false)
const banner = ref(route.query.expired ? '登录已过期,请重新登录' : '')

async function submit() {
  busy.value = true
  banner.value = ''
  try {
    const res = await api.login({ username: username.value, password: password.value })
    auth.login(res)
    router.push('/dashboard')
  } catch (e) {
    banner.value = e.message || '登录失败'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.login-page { height: 100%; display: flex; align-items: center; justify-content: center; }
.login-card { width: 400px; }
.head { text-align: center; margin-bottom: 24px; }
.logo { font-size: 32px; display: block; margin-bottom: 8px; }
.head h1 { font-size: 18px; }
.banner {
  background: #fdecec; color: var(--danger); border-radius: var(--radius-ctl);
  padding: 8px 12px; margin-bottom: 16px; font-size: 13px;
}
.full { width: 100%; }
.hint { text-align: center; margin-top: 16px; }
</style>
