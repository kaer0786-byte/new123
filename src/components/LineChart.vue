<template>
  <div class="chart-root" ref="rootEl">
    <!-- 图例:≥2 系列必备(DESIGN_SPEC §2.1 图表硬性规则) -->
    <div class="legend">
      <span v-for="s in series" :key="s.name" class="legend-item">
        <span class="swatch" :style="{ background: s.color }" aria-hidden="true"></span>{{ s.name }}
      </span>
    </div>

    <svg
      :viewBox="`0 0 ${W} ${H}`" class="plot" role="img" :aria-label="ariaLabel"
      @mousemove="onMove" @mouseleave="hoverIdx = -1"
    >
      <!-- 网格(recessive) -->
      <line
        v-for="(t, i) in yTicks" :key="'g' + i"
        :x1="PAD.l" :x2="W - PAD.r" :y1="yPos(t)" :y2="yPos(t)"
        stroke="#eef0f2" stroke-width="1"
      />
      <text v-for="(t, i) in yTicks" :key="'yt' + i" :x="PAD.l - 8" :y="yPos(t) + 4" class="tick" text-anchor="end">{{ t }}</text>
      <text v-for="(label, i) in labels" :key="'xt' + i" :x="xPos(i)" :y="H - 8" class="tick" text-anchor="middle">{{ label }}</text>

      <!-- crosshair -->
      <line
        v-if="hoverIdx >= 0"
        :x1="xPos(hoverIdx)" :x2="xPos(hoverIdx)" :y1="PAD.t" :y2="H - PAD.b"
        stroke="#c6cbd2" stroke-width="1" stroke-dasharray="3 3"
      />

      <!-- 数据线 2px + hover 点 -->
      <template v-for="s in series" :key="s.name">
        <polyline :points="polyPoints(s)" fill="none" :stroke="s.color" stroke-width="2" stroke-linejoin="round" />
        <circle
          v-if="hoverIdx >= 0"
          :cx="xPos(hoverIdx)" :cy="yPos(s.data[hoverIdx])" r="4"
          :fill="s.color" stroke="#fff" stroke-width="2"
        />
      </template>
    </svg>

    <!-- 联合 tooltip:文字用文本色,系列色仅作前置色点 -->
    <div v-if="hoverIdx >= 0" class="tooltip" :style="tooltipStyle">
      <div class="tt-title">{{ labels[hoverIdx] }}</div>
      <div v-for="s in series" :key="s.name" class="tt-row">
        <span class="swatch" :style="{ background: s.color }"></span>
        <span>{{ s.name }}</span>
        <b>{{ s.data[hoverIdx] }}</b>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  labels: { type: Array, required: true },                 // x 轴标签
  series: { type: Array, required: true },                 // [{name, color, data:[Number]}]
  ariaLabel: { type: String, default: '折线图' }
})

const W = 760, H = 300
const PAD = { l: 40, r: 16, t: 16, b: 28 }
const rootEl = ref(null)
const hoverIdx = ref(-1)

const maxY = computed(() => {
  const m = Math.max(1, ...props.series.flatMap(s => s.data))
  return Math.ceil(m / 4) * 4
})
const yTicks = computed(() => [0, 1, 2, 3, 4].map(i => (maxY.value / 4) * i))

const xPos = i => PAD.l + (i * (W - PAD.l - PAD.r)) / Math.max(1, props.labels.length - 1)
const yPos = v => H - PAD.b - (v / maxY.value) * (H - PAD.t - PAD.b)
const polyPoints = s => s.data.map((v, i) => `${xPos(i)},${yPos(v)}`).join(' ')

function onMove(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  const x = ((e.clientX - rect.left) / rect.width) * W
  const step = (W - PAD.l - PAD.r) / Math.max(1, props.labels.length - 1)
  hoverIdx.value = Math.min(props.labels.length - 1, Math.max(0, Math.round((x - PAD.l) / step)))
}

const tooltipStyle = computed(() => {
  if (hoverIdx.value < 0 || !rootEl.value) return {}
  const frac = xPos(hoverIdx.value) / W
  return frac > 0.7
    ? { right: `${(1 - frac) * 100 + 2}%`, top: '48px' }
    : { left: `${frac * 100 + 2}%`, top: '48px' }
})
</script>

<style scoped>
.chart-root { position: relative; }
.legend { display: flex; gap: 16px; margin-bottom: 8px; }
.legend-item { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-secondary); }
.swatch { width: 10px; height: 10px; border-radius: 2px; display: inline-block; flex: none; }
.plot { width: 100%; height: auto; display: block; }
.tick { font-size: 11px; fill: var(--text-muted); }
.tooltip {
  position: absolute; z-index: 10; pointer-events: none;
  background: var(--bg-card); border: 1px solid var(--border);
  border-radius: var(--radius-ctl); box-shadow: var(--shadow-float);
  padding: 8px 12px; font-size: 12px; min-width: 120px;
}
.tt-title { color: var(--text-secondary); margin-bottom: 4px; }
.tt-row { display: flex; align-items: center; gap: 6px; line-height: 20px; }
.tt-row b { margin-left: auto; font-variant-numeric: tabular-nums; }
</style>
