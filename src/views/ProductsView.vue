<template>
  <div class="card">
    <!-- 筛选行 -->
    <div class="filter-row">
      <div class="status-tabs" role="tablist">
        <button
          v-for="s in STATUS_TABS" :key="s.value"
          class="tab" :class="{ active: status === s.value }"
          @click="status = s.value; page = 1; load()"
        >{{ s.label }}</button>
      </div>
      <input
        v-model.trim="keyword" class="input search" placeholder="搜索标题…"
        @keyup.enter="page = 1; load()"
      />
      <button
        v-if="selected.length" class="btn btn-secondary"
        @click="batchDeleteOpen = true"
      >批量删除({{ selected.length }})</button>
      <button
        v-if="selected.length" class="btn btn-secondary"
        :disabled="busy" @click="batchMarkPending"
      >批量标记待发布</button>
    </div>

    <TableState :loading="loading" :error="error" :empty="!items.length" empty-text="还没有采集到商品" @retry="load">
      <template #empty-action>
        <p class="caption">在手机端 App 打开 1688 商品页,点击悬浮球「采集此商品」</p>
      </template>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th style="width:36px"><input type="checkbox" :checked="allChecked" @change="toggleAll" aria-label="全选" /></th>
              <th style="width:76px">主图</th>
              <th>标题(优化后)</th>
              <th style="width:90px">采集价</th>
              <th style="width:90px">售价</th>
              <th style="width:100px">状态</th>
              <th style="width:130px">采集时间</th>
              <th style="width:110px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in items" :key="p.id" class="row" @click="openEdit(p.id)">
              <td @click.stop><input type="checkbox" :value="p.id" v-model="selected" :aria-label="`选择 ${p.title}`" /></td>
              <td><img :src="p.image" class="thumb" :alt="p.title" /></td>
              <td><div class="title-cell">{{ p.selectedTitle || p.title }}</div></td>
              <td class="mono">{{ fmtPrice(p.costPrice) }}</td>
              <td class="mono">{{ fmtPrice(p.price) }}</td>
              <td><StatusTag :status="p.status" /></td>
              <td class="caption">{{ fmtTime(p.collectedAt) }}</td>
              <td @click.stop>
                <button class="link-btn" @click="openEdit(p.id)">编辑</button>
                <button class="link-btn danger" style="margin-left:12px" @click="askDelete(p)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination :total="total" :page="page" @change="p => { page = p; load() }" />
    </TableState>

    <!-- 编辑抽屉 -->
    <ProductDrawer :product-id="editingId" @close="editingId = null" @saved="load" />

    <!-- 删除确认 -->
    <ConfirmDialog
      :open="!!deleting" danger title="删除商品" :busy="busy"
      :message="`确定删除商品「${deleting?.title || ''}」吗?删除后不可恢复。`"
      confirm-text="删除" @cancel="deleting = null" @confirm="doDelete"
    />
    <ConfirmDialog
      :open="batchDeleteOpen" danger title="批量删除" :busy="busy"
      :message="`确定删除选中的 ${selected.length} 个商品吗?删除后不可恢复。`"
      confirm-text="删除" @cancel="batchDeleteOpen = false" @confirm="doBatchDelete"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '@/api'
import { toast } from '@/store/toast'
import { fmtPrice, fmtTime } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import TableState from '@/components/TableState.vue'
import Pagination from '@/components/Pagination.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ProductDrawer from '@/views/ProductDrawer.vue'

const STATUS_TABS = [
  { label: '全部', value: '' }, { label: '草稿', value: 'draft' },
  { label: '待发布', value: 'pending' }, { label: '已发布', value: 'published' },
  { label: '发布失败', value: 'failed' }
]

const items = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const keyword = ref('')
const loading = ref(true)
const error = ref('')
const busy = ref(false)

const selected = ref([])
const editingId = ref(null)
const deleting = ref(null)
const batchDeleteOpen = ref(false)

const allChecked = computed(() => items.value.length > 0 && selected.value.length === items.value.length)

async function load() {
  loading.value = true
  error.value = ''
  selected.value = []
  try {
    const res = await api.listProducts({ status: status.value, keyword: keyword.value, page: page.value })
    items.value = res.items
    total.value = res.total
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function toggleAll() {
  selected.value = allChecked.value ? [] : items.value.map(p => p.id)
}

const openEdit = id => { editingId.value = id }
const askDelete = p => { deleting.value = p }

async function doDelete() {
  busy.value = true
  try {
    await api.deleteProducts([deleting.value.id])
    toast('已删除')
    deleting.value = null
    load()
  } catch (e) {
    toast(e.message || '删除失败', 'error')
  } finally {
    busy.value = false
  }
}

async function doBatchDelete() {
  busy.value = true
  try {
    await api.deleteProducts([...selected.value])
    toast(`已删除 ${selected.value.length} 个商品`)
    batchDeleteOpen.value = false
    load()
  } catch (e) {
    toast(e.message || '删除失败', 'error')
  } finally {
    busy.value = false
  }
}

async function batchMarkPending() {
  busy.value = true
  try {
    await Promise.all(selected.value.map(id => api.updateProduct(id, { status: 'pending' })))
    toast(`已标记 ${selected.value.length} 个商品为待发布`)
    load()
  } catch (e) {
    toast(e.message || '操作失败', 'error')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.filter-row { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.status-tabs { display: flex; gap: 4px; }
.tab {
  height: 32px; padding: 0 12px; border: none; background: none; cursor: pointer;
  border-radius: var(--radius-ctl); font-size: 14px; font-family: inherit; color: var(--text-secondary);
}
.tab:hover { background: var(--bg-page); }
.tab.active { background: var(--primary-bg); color: var(--primary); }
.search { width: 220px; margin-left: auto; }
.thumb { width: 56px; height: 56px; border-radius: 4px; object-fit: cover; display: block; }
.row { cursor: pointer; }
.title-cell {
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden; max-width: 420px;
}
</style>
