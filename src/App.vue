<template>
  <router-view />
  <!-- 全局 toast -->
  <div class="toast-host" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.type">
        <span v-if="t.type === 'success'">✓</span>
        <span v-else>✕</span>
        {{ t.message }}
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { toasts } from '@/store/toast'
</script>

<style scoped>
.toast-host {
  position: fixed; top: 16px; right: 16px; z-index: 1000;
  display: flex; flex-direction: column; gap: 8px;
}
.toast {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px; border-radius: var(--radius-ctl);
  background: var(--bg-card); box-shadow: var(--shadow-float);
  border: 1px solid var(--border); font-size: 14px;
}
.toast.success span { color: var(--success); }
.toast.error span { color: var(--danger); }
.toast-enter-active, .toast-leave-active { transition: all 0.2s ease-out; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(12px); }
</style>
