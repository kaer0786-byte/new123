<template>
  <div class="settings">
    <div v-if="loading" class="card center"><span class="spinner dark"></span></div>

    <template v-else>
      <!-- 加价规则 -->
      <div class="card block">
        <h2>加价规则</h2>
        <div class="field">
          <label>加价方式</label>
          <div class="radio-row">
            <label><input type="radio" value="PERCENT" v-model="form.markupType" /> 百分比加价</label>
            <label><input type="radio" value="FIXED" v-model="form.markupType" /> 固定金额加价</label>
          </div>
        </div>
        <div class="field" style="max-width:240px">
          <label for="mv">{{ form.markupType === 'PERCENT' ? '加价比例(%)' : '加价金额(元)' }}</label>
          <input id="mv" class="input" type="number" min="0" step="0.1" v-model.number="form.markupValue" />
        </div>
        <p class="caption example">示例:采集价 ¥10.00 → 售价 <b>{{ markupExample }}</b></p>
        <button class="btn btn-primary" :disabled="saving" @click="save('加价规则')">保存</button>
      </div>

      <!-- AI 配置 -->
      <div class="card block">
        <h2>AI 配置</h2>
        <div class="field">
          <label>模型</label>
          <div class="radio-row">
            <label><input type="radio" value="tongyi" v-model="form.aiModel" /> 通义千问</label>
            <label><input type="radio" value="openai" v-model="form.aiModel" /> OpenAI 兼容</label>
          </div>
        </div>
        <div class="field" style="max-width:420px">
          <label for="key">API Key</label>
          <div class="key-row">
            <input
              id="key" class="input" :type="showKey ? 'text' : 'password'"
              v-model.trim="form.aiApiKey" placeholder="sk-…"
            />
            <button class="link-btn" @click="showKey = !showKey">{{ showKey ? '隐藏' : '显示' }}</button>
          </div>
          <p class="caption">出于安全考虑,已保存的 Key 仅显示末 4 位;重新输入完整 Key 即可更换</p>
        </div>
        <div style="display:flex;gap:12px">
          <button class="btn btn-primary" :disabled="saving" @click="save('AI 配置')">保存</button>
          <button class="btn btn-secondary" :disabled="testing" @click="testConnection">
            <span v-if="testing" class="spinner dark"></span>测试连接
          </button>
        </div>
      </div>

      <!-- 发布默认项 -->
      <div class="card block">
        <h2>发布默认项</h2>
        <div class="grid-2">
          <div class="field">
            <label for="st">默认运费模板</label>
            <input id="st" class="input" v-model.trim="form.shippingTemplate" />
          </div>
          <div class="field">
            <label for="dp">默认发货地</label>
            <input id="dp" class="input" v-model.trim="form.deliveryPlace" />
          </div>
        </div>
        <div class="field">
          <label class="check-row">
            <input type="checkbox" v-model="form.autoSubmit" />
            自动提交发布(关闭时保存到草稿箱,人工确认后发布 — 推荐)
          </label>
        </div>
        <button class="btn btn-primary" :disabled="saving" @click="save('发布默认项')">保存</button>
      </div>

      <!-- 操作速度 -->
      <div class="card block">
        <h2>操作速度</h2>
        <p class="caption" style="margin-bottom:16px">App 端每步操作之间的延迟区间(毫秒)</p>
        <div class="grid-2" style="max-width:420px">
          <div class="field">
            <label for="min">最小延迟</label>
            <input id="min" class="input" type="number" min="300" max="3000" step="100" v-model.number="form.minDelayMs" />
          </div>
          <div class="field">
            <label for="max">最大延迟</label>
            <input id="max" class="input" type="number" min="300" max="3000" step="100" v-model.number="form.maxDelayMs" />
          </div>
        </div>
        <p v-if="delayInvalid" class="caption" style="color:var(--danger);margin-bottom:8px">最大延迟必须不小于最小延迟</p>
        <button class="btn btn-primary" :disabled="saving || delayInvalid" @click="save('操作速度')">保存</button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '@/api'
import { toast } from '@/store/toast'

const loading = ref(true)
const saving = ref(false)
const testing = ref(false)
const showKey = ref(false)
const form = ref({})

const markupExample = computed(() => {
  const v = Number(form.value.markupValue) || 0
  const price = form.value.markupType === 'PERCENT' ? 10 * (1 + v / 100) : 10 + v
  return '¥' + price.toFixed(2)
})

const delayInvalid = computed(() => Number(form.value.maxDelayMs) < Number(form.value.minDelayMs))

async function save(name) {
  saving.value = true
  try {
    await api.updateSettings({ ...form.value })
    toast(`${name}已保存,已同步至 App`)
  } catch (e) {
    toast(e.message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  testing.value = true
  // Mock 环境模拟连通性测试;真实环境应由后端代理调用 AI 服务
  await new Promise(r => setTimeout(r, 800))
  testing.value = false
  toast(form.value.aiApiKey ? '连接成功' : '请先填写 API Key', form.value.aiApiKey ? 'success' : 'error')
}

onMounted(async () => {
  form.value = await api.getSettings()
  loading.value = false
})
</script>

<style scoped>
.settings { max-width: 720px; }
.center { display: flex; justify-content: center; padding: 48px 0; }
.block { margin-bottom: 16px; }
.block h2 { margin-bottom: 16px; }
.radio-row { display: flex; gap: 24px; }
.radio-row label, .check-row { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.example { margin-bottom: 16px; }
.key-row { display: flex; gap: 12px; align-items: center; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
</style>
