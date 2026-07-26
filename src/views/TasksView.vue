<template>
  <div class="card">
    <div class="filter-row">
      <select v-model="type" class="select w140" aria-label="任务类型" @change="page = 1; load()">
        <option value="">全部类型</option>
        <option value="collect">采集</option>
        <option value="publish">发布</option>
      </select>
      <select v-model="status" class="select w140" aria-label="任务状态" @change="page = 1; load()">
        <option value="">全部状态</option>
        <option value="success">成功</option>
        <option value="failed">失败</option>
        <option value="running">进行中</option>
      </select>
    </div>

    <TableState :loading="loading" :error="error" :empty="!items.length" empty-text="暂无任务记录" @retry="load">
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th style="width:80px">类型</th>
              <th>关联商品</th>
              <th style="width:100px">状态</th>
              <th style="width:130px">开始时间</th>
              <th style="width:80px">耗时</th>
              <th style="width:110px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in items" :key="t.id" class="row" @click="$router.push(`/tasks/${t.id}`)">
              <td><StatusTag :status="t.type" /></td>
              <td><div class="t">{{ t.productTitle }}</div></td>
              <td><StatusTag :status="t.status" /></td>
              <td class="caption">{{ fmtTime(t.startedAt) }}</td>
              <td class="caption">{{ fmtDuration(t.durationSec) }}</td>
              <td @click.stop>
                <router-link :to="`/tasks/${t.id}`" class="link-btn" style="text-decoration:none">详情</router-link>
                <button
                  v-if="t.status === 'failed'" class="link-btn" style="margin-left:12px"
                  :disabled="retryingId === t.id" @click="retry(t)"
                >重试</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination :total="total" :page="page" @change="p => { page = p; load() }" />
    </TableState>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api'
import { toast } from '@/store/toast'
import { fmtDuration, fmtTime } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import TableState from '@/components/TableState.vue'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const items = ref([])
const total = ref(0)
const page = ref(1)
const type = ref('')
const status = ref(typeof route.query.status === 'string' ? route.query.status : '')
const loading = ref(true)
const error = ref('')
const retryingId = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await api.listTasks({ type: type.value, status: status.value, page: page.value })
    items.value = res.items
    total.value = res.total
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function retry(t) {
  retryingId.value = t.id
  try {
    const res = await api.retryTask(t.id)
    toast(res.message || '重试指令已下发')
    load()
  } catch (e) {
    toast(e.message || '重试失败', 'error')
  } finally {
    retryingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.filter-row { display: flex; gap: 12px; margin-bottom: 16px; }
.w140 { width: 140px; }
.row { cursor: pointer; }
.t { max-width: 480px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
