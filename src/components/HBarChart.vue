<template>
  <!-- 横向条形图:每条带直接数值标签(浅色系列对比度兜底,DESIGN_SPEC §2.1) -->
  <div class="hbar" role="img" :aria-label="ariaLabel">
    <div v-for="(row, i) in rows" :key="row.label" class="row" :title="`${row.label}:${row.value}`">
      <span class="label">{{ row.label }}</span>
      <div class="track">
        <div
          class="bar"
          :style="{ width: pct(row.value) + '%', background: colors[i % colors.length] }"
        ></div>
        <span class="value">{{ row.value }}</span>
      </div>
    </div>
    <p v-if="!rows.length" class="caption" style="text-align:center;padding:24px 0">暂无失败记录 🎉</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  rows: { type: Array, required: true },   // [{label, value}],调用方需已按 >5 类合并「其他」
  ariaLabel: { type: String, default: '条形图' }
})

// 图表槽位固定顺序(series-1..5)
const colors = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4']
const max = computed(() => Math.max(1, ...props.rows.map(r => r.value)))
const pct = v => Math.max(2, (v / max.value) * 100)
</script>

<style scoped>
.hbar { display: flex; flex-direction: column; gap: 10px; }
.row { display: flex; align-items: center; gap: 12px; }
.label {
  width: 96px; flex: none; text-align: right;
  font-size: 12px; color: var(--text-secondary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.track { flex: 1; display: flex; align-items: center; gap: 8px; }
.bar { height: 14px; border-radius: 0 4px 4px 0; min-width: 4px; }
.value { font-size: 12px; font-variant-numeric: tabular-nums; color: var(--text-primary); }
</style>
