<template>
  <div class="pager">
    <span class="caption">共 {{ total }} 条</span>
    <button class="btn btn-secondary btn-sm" :disabled="page <= 1" @click="$emit('change', page - 1)">‹ 上一页</button>
    <span class="caption">{{ page }} / {{ pages }}</span>
    <button class="btn btn-secondary btn-sm" :disabled="page >= pages" @click="$emit('change', page + 1)">下一页 ›</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 }
})
defineEmits(['change'])
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
</script>

<style scoped>
.pager {
  display: flex; align-items: center; justify-content: flex-end; gap: 12px;
  padding-top: 16px;
}
</style>
