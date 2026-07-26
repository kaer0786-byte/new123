<template>
  <div>
    <button class="link-btn back" @click="$router.back()">← 返回</button>

    <div v-if="loading" class="card center"><span class="spinner dark"></span></div>
    <div v-else-if="error" class="card center">
      <p class="caption">{{ error }}</p>
      <button class="btn btn-secondary" @click="load">重试</button>
    </div>

    <template v-else-if="task">
      <div class="card head">
        <img :src="task.productImage" class="thumb" :alt="task.productTitle" />
        <div class="info">
          <h1>
            {{ task.type === 'collect' ? '采集' : '发布' }}任务 #{{ task.id }}
            <StatusTag :status="task.status" />
          </h1>
          <p class="caption">{{ task.productTitle }}</p>
          <p class="caption">开始:{{ fmtTime(task.startedAt) }} · 耗时 {{ fmtDuration(task.durationSec) }}</p>
        </div>
        <button
          v-if="task.status === 'failed'" class="btn btn-primary"
          :disabled="busy" @click="retry"
        ><span v-if="busy" class="spinner"></span>重试该任务</button>
      </div>

      <div class="cols">
        <!-- 步骤时间线 -->
        <div class="card">
          <h2>步骤时间线</h2>
          <ol class="timeline">
            <li v-for="(s, i) in task.steps" :key="i" :class="s.status">
              <span class="mark" aria-hidden="true">
                {{ s.status === 'success' ? '✓' : s.status === 'failed' ? '✗' : '·' }}
              </span>
              <div class="step-body">
                <div class="step-head">
                  <span class="name">{{ s.name }}</span>
                  <span class="caption">{{ fmtClock(s.at) }}</span>
                </div>
                <p v-if="s.detail" class="detail mono">{{ s.detail }}</p>
              </div>
            </li>
          </ol>
        </div>

        <!-- 失败截图 -->
        <div class="card">
          <h2>失败现场截图</h2>
          <template v-if="task.screenshot">
            <img
              :src="task.screenshot" class="shot" alt="失败现场截图,点击放大"
              @click="lightbox = true"
            />
            <p class="caption" style="margin-top:8px">错误信息:</p>
            <p class="mono err">{{ task.failReason }}{{ failedStep?.detail ? ' — ' + failedStep.detail : '' }}</p>
          </template>
          <p v-else class="caption" style="padding:24px 0;text-align:center">该任务无失败截图</p>
        </div>
      </div>

      <!-- 灯箱 -->
      <Teleport to="body">
        <div v-if="lightbox" class="lightbox" @click="lightbox = false">
          <img :src="task.screenshot" alt="失败现场截图(放大)" />
        </div>
      </Teleport>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api'
import { toast } from '@/store/toast'
import { fmtDuration, fmtTime } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'

const route = useRoute()
const task = ref(null)
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const lightbox = ref(false)

const failedStep = computed(() => task.value?.steps.find(s => s.status === 'failed'))

const fmtClock = iso => {
  const d = new Date(iso)
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    task.value = await api.getTask(route.params.id)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function retry() {
  busy.value = true
  try {
    const res = await api.retryTask(task.value.id)
    toast(res.message || '重试指令已下发')
    load()
  } catch (e) {
    toast(e.message || '重试失败', 'error')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.back { margin-bottom: 16px; display: inline-block; }
.center { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 48px 0; }
.head { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.head .thumb { width: 64px; height: 64px; border-radius: 4px; object-fit: cover; }
.head .info { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.head h1 { display: flex; align-items: center; gap: 12px; }
.cols { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; align-items: start; }
@media (max-width: 1279px) { .cols { grid-template-columns: 1fr; } }

.timeline { list-style: none; }
.timeline li { display: flex; gap: 12px; padding: 10px 0; }
.mark {
  width: 24px; height: 24px; flex: none; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; background: var(--bg-page); color: var(--text-muted);
}
li.success .mark { background: #e8f5e8; color: var(--success); }
li.failed .mark { background: #fdecec; color: var(--danger); }
.step-body { flex: 1; min-width: 0; }
.step-head { display: flex; justify-content: space-between; gap: 12px; }
li.failed .name { color: var(--danger); font-weight: 600; }
.detail { font-size: 12px; color: var(--danger); margin-top: 4px; word-break: break-all; }

.shot {
  width: 100%; max-width: 280px; display: block; cursor: zoom-in;
  border: 1px solid var(--border); border-radius: var(--radius-card);
}
.err { color: var(--danger); font-size: 13px; word-break: break-all; }
.lightbox {
  position: fixed; inset: 0; z-index: 950; background: var(--overlay);
  display: flex; align-items: center; justify-content: center; cursor: zoom-out;
}
.lightbox img { max-height: 90vh; max-width: 90vw; border-radius: var(--radius-card); }
</style>
