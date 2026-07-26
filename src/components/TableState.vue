<template>
  <!-- 列表三态:加载骨架 / 错误重试 / 空状态(DESIGN_SPEC §3 表格) -->
  <div v-if="loading">
    <div v-for="i in 5" :key="i" class="skeleton-row">
      <div class="skeleton-block" style="max-width: 60px"></div>
      <div class="skeleton-block"></div>
      <div class="skeleton-block" style="max-width: 120px"></div>
    </div>
  </div>
  <div v-else-if="error" class="state-box">
    <div class="state-icon">⚠️</div>
    <p class="caption">{{ error }}</p>
    <button class="btn btn-secondary" @click="$emit('retry')">重试</button>
  </div>
  <div v-else-if="empty" class="state-box">
    <div class="state-icon">🗂️</div>
    <p class="caption">{{ emptyText }}</p>
    <slot name="empty-action" />
  </div>
  <slot v-else />
</template>

<script setup>
defineProps({
  loading: Boolean,
  error: { type: String, default: '' },
  empty: Boolean,
  emptyText: { type: String, default: '暂无数据' }
})
defineEmits(['retry'])
</script>

<style scoped>
.state-box {
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 48px 0;
}
.state-icon { font-size: 32px; }
</style>
