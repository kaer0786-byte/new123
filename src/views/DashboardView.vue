<template>
  <div>
    <!-- 新手三步引导(可关闭,记忆) -->
    <div v-if="showOnboard" class="card onboard">
      <div class="steps">
        <div class="step">
          <span class="step-num">1</span>
          <div><b>连接手机 App</b><p class="caption">在安卓手机安装助手 App 并登录同一账号</p></div>
        </div>
        <span class="arrow" aria-hidden="true">→</span>
        <div class="step">
          <span class="step-num">2</span>
          <div><b>采集 1688 商品</b><p class="caption">粘贴商品链接,或在手机上点悬浮球采集</p></div>
        </div>
        <span class="arrow" aria-hidden="true">→</span>
        <div class="step">
          <span class="step-num">3</span>
          <div><b>一键发布到千牛</b><p class="caption">确认标题价格后发布,任务日志跟踪进度</p></div>
        </div>
      </div>
      <div class="onboard-actions">
        <router-link to="/products" class="btn btn-primary">开始采集商品</router-link>
        <button class="link-btn" @click="dismissOnboard">不再显示</button>
      </div>
    </div>

    <div class="head-row">
      <span></span>
      <select v-model.number="days" class="select range" aria-label="趋势时间范围">
        <option :value="7">近 7 天</option>
        <option :value="30">近 30 天</option>
      </select>
    </div>

    <!-- 指标卡 -->
    <div class="metrics">
      <div class="card metric">
        <p class="caption">今日采集</p>
        <p class="num">{{ summary?.todayCollected ?? '—' }}</p>
      </div>
      <div class="card metric">
        <p class="caption">今日发布</p>
        <p class="num">{{ summary?.todayPublished ?? '—' }}</p>
      </div>
      <div class="card metric">
        <p class="caption">发布成功率</p>
        <p class="num">{{ summary ? summary.successRate + '%' : '—' }}</p>
      </div>
      <router-link class="card metric clickable" to="/tasks?status=failed" title="点击查看失败任务">
        <p class="caption">待处理失败 ⚠</p>
        <p class="num" :class="{ danger: summary?.pendingFailures > 0 }">{{ summary?.pendingFailures ?? '—' }}</p>
        <p class="caption view-more">查看详情 →</p>
      </router-link>
    </div>

    <!-- 趋势图 -->
    <div class="card block">
      <h2>采集与发布趋势</h2>
      <div v-if="trendLoading" class="chart-loading"><span class="spinner dark"></span></div>
      <LineChart
        v-else
        :labels="trend.map(t => t.date)"
        :series="[
          { name: '采集', color: 'var(--series-1)', data: trend.map(t => t.collected) },
          { name: '发布', color: 'var(--series-2)', data: trend.map(t => t.published) }
        ]"
        :aria-label="`近${days}天采集与发布数量趋势折线图`"
      />
    </div>

    <div class="two-col">
      <div class="card block">
        <h2>失败原因分布(近 30 天)</h2>
        <HBarChart :rows="failureRows" aria-label="失败原因分布条形图" />
      </div>
      <div class="card block">
        <h2>最近失败任务</h2>
        <p v-if="!recentFailed.length" class="caption" style="padding:24px 0;text-align:center">暂无失败任务 🎉</p>
        <ul class="fail-list">
          <li v-for="t in recentFailed" :key="t.id">
            <StatusTag :status="t.type" />
            <router-link :to="`/tasks/${t.id}`" class="title" :title="t.productTitle">{{ t.productTitle }}</router-link>
            <span class="caption">{{ fmtTime(t.startedAt) }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '@/api'
import LineChart from '@/components/LineChart.vue'
import HBarChart from '@/components/HBarChart.vue'
import StatusTag from '@/components/StatusTag.vue'
import { fmtTime } from '@/utils/format'

const days = ref(7)
const showOnboard = ref(localStorage.getItem('onboardDismissed') !== '1')
const dismissOnboard = () => { showOnboard.value = false; localStorage.setItem('onboardDismissed', '1') }
const summary = ref(null)
const trend = ref([])
const trendLoading = ref(true)
const failures = ref([])
const recentFailed = ref([])

// >5 类合并「其他」(DESIGN_SPEC §2.1 图表硬性规则)
const failureRows = computed(() => {
  const rows = failures.value.map(f => ({ label: f.reason, value: f.count }))
  if (rows.length <= 5) return rows
  const head = rows.slice(0, 5)
  const rest = rows.slice(5).reduce((s, r) => s + r.value, 0)
  return [...head, { label: '其他', value: rest }]
})

async function loadTrend() {
  trendLoading.value = true
  trend.value = await api.dashboardTrend({ days: days.value })
  trendLoading.value = false
}

onMounted(async () => {
  loadTrend()
  summary.value = await api.dashboardSummary()
  failures.value = await api.dashboardFailures()
  const res = await api.listTasks({ status: 'failed', page: 1 })
  recentFailed.value = res.items.slice(0, 5)
})
watch(days, loadTrend)
</script>

<style scoped>
.head-row { display: flex; justify-content: space-between; margin-bottom: 16px; }
.range { width: 120px; }
.metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
@media (max-width: 1279px) { .metrics { grid-template-columns: repeat(2, 1fr); } }
.metric .num {
  font-size: 28px; line-height: 36px; font-weight: 600;
  font-variant-numeric: tabular-nums; margin-top: 4px;
}
.metric .num.danger { color: var(--danger); }
.metric.clickable { display: block; color: inherit; }
.metric.clickable:hover { border-color: var(--primary); }
.view-more { color: var(--primary); margin-top: 4px; }
.onboard { margin-bottom: 16px; display: flex; align-items: center; gap: 24px; flex-wrap: wrap; }
.steps { display: flex; align-items: center; gap: 16px; flex: 1; flex-wrap: wrap; }
.step { display: flex; gap: 10px; align-items: flex-start; }
.step-num {
  width: 24px; height: 24px; flex: none; border-radius: 50%;
  background: var(--primary-bg); color: var(--primary);
  display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 13px;
}
.arrow { color: var(--text-muted); }
.onboard-actions { display: flex; align-items: center; gap: 16px; }
.block { margin-bottom: 16px; }
.block h2 { margin-bottom: 16px; }
.chart-loading { height: 300px; display: flex; align-items: center; justify-content: center; }
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 1279px) { .two-col { grid-template-columns: 1fr; } }
.fail-list { list-style: none; }
.fail-list li {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 0; border-bottom: 1px solid var(--border);
}
.fail-list li:last-child { border-bottom: none; }
.fail-list .title {
  flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  color: var(--text-primary);
}
.fail-list .title:hover { color: var(--primary); }
</style>
