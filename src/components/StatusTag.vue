<template>
  <span class="tag" :class="conf.cls">
    <span v-if="status === 'running'" class="spin">⟳</span>
    <span v-else class="dot" aria-hidden="true"></span>
    {{ conf.label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ status: { type: String, required: true } })

const MAP = {
  draft: { label: '草稿', cls: 'tag-gray' },
  pending: { label: '待发布', cls: 'tag-blue' },
  published: { label: '已发布', cls: 'tag-green' },
  success: { label: '成功', cls: 'tag-green' },
  failed: { label: '失败', cls: 'tag-red' },
  running: { label: '进行中', cls: 'tag-blue' },
  admin: { label: '管理员', cls: 'tag-blue' },
  operator: { label: '操作员', cls: 'tag-gray' },
  collect: { label: '采集', cls: 'tag-gray' },
  publish: { label: '发布', cls: 'tag-blue' }
}
const conf = computed(() => MAP[props.status] || { label: props.status, cls: 'tag-gray' })
</script>
