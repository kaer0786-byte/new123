<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="overlay" @click.self="$emit('close')"></div>
    </Transition>
    <Transition name="slide">
      <div v-if="open" class="drawer" :style="{ width: width + 'px' }" role="dialog" aria-modal="true" :aria-label="title">
        <header>
          <h2>{{ title }}</h2>
          <button class="close" aria-label="关闭" @click="$emit('close')">✕</button>
        </header>
        <div class="body"><slot /></div>
        <footer v-if="$slots.footer"><slot name="footer" /></footer>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'

const props = defineProps({
  open: Boolean,
  title: { type: String, default: '' },
  width: { type: Number, default: 560 }
})
const emit = defineEmits(['close'])

// Esc 关闭需要全局监听:overlay div 不可聚焦,元素级 keydown 收不到事件
const onKey = e => { if (e.key === 'Escape' && props.open) emit('close') }
onMounted(() => document.addEventListener('keydown', onKey))
onUnmounted(() => document.removeEventListener('keydown', onKey))
</script>

<style scoped>
.overlay { position: fixed; inset: 0; background: var(--overlay); z-index: 800; }
.drawer {
  position: fixed; top: 0; right: 0; bottom: 0; z-index: 810;
  max-width: 92vw;
  background: var(--bg-card); box-shadow: var(--shadow-float);
  display: flex; flex-direction: column;
}
header {
  height: 56px; flex: none; padding: 0 24px;
  display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1px solid var(--border);
}
.close {
  background: none; border: none; cursor: pointer; font-size: 16px;
  color: var(--text-secondary); padding: 4px 8px;
}
.close:hover { color: var(--text-primary); }
.body { flex: 1; overflow: auto; padding: 24px; }
footer {
  flex: none; padding: 12px 24px; border-top: 1px solid var(--border);
  display: flex; justify-content: space-between; align-items: center; gap: 12px;
  background: var(--bg-card);
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease-out; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.slide-enter-active, .slide-leave-active { transition: transform 0.2s ease-out; }
.slide-enter-from, .slide-leave-to { transform: translateX(100%); }
</style>
