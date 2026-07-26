<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="overlay" @click.self="cancel">
        <div class="dialog" role="dialog" aria-modal="true" :aria-label="title">
          <h2>{{ title }}</h2>
          <p class="desc">{{ message }}</p>
          <div class="actions">
            <!-- 取消为默认焦点(DESIGN_SPEC §1.3) -->
            <button ref="cancelBtn" class="btn btn-secondary" @click="cancel">取消</button>
            <button class="btn" :class="danger ? 'btn-danger' : 'btn-primary'" :disabled="busy" @click="confirm">
              <span v-if="busy" class="spinner"></span>{{ confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps({
  open: Boolean,
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '' },
  confirmText: { type: String, default: '确认' },
  danger: Boolean,
  busy: Boolean
})
const emit = defineEmits(['confirm', 'cancel'])

const cancelBtn = ref(null)
watch(() => props.open, async v => {
  if (v) { await nextTick(); cancelBtn.value?.focus() }
})

const confirm = () => emit('confirm')
const cancel = () => !props.busy && emit('cancel')

const onKey = e => { if (e.key === 'Escape' && props.open) cancel() }
onMounted(() => document.addEventListener('keydown', onKey))
onUnmounted(() => document.removeEventListener('keydown', onKey))
</script>

<style scoped>
.overlay {
  position: fixed; inset: 0; background: var(--overlay); z-index: 900;
  display: flex; align-items: center; justify-content: center;
}
.dialog {
  width: 420px; background: var(--bg-card); border-radius: var(--radius-card);
  box-shadow: var(--shadow-modal); padding: 24px;
}
.desc { margin: 12px 0 24px; color: var(--text-secondary); }
.actions { display: flex; justify-content: flex-end; gap: 12px; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease-out; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
