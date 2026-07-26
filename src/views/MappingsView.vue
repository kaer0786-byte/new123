<template>
  <div class="card">
    <div class="filter-row">
      <h2>1688 ↔ 淘宝 商品映射</h2>
      <input v-model.trim="keyword" class="input search" placeholder="搜索标题 / 淘宝ID…" @keyup.enter="page = 1; load()" />
    </div>

    <TableState :loading="loading" :error="error" :empty="!items.length" empty-text="还没有映射记录,发布成功后自动生成" @retry="load">
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>1688 商品</th>
              <th>淘宝商品</th>
              <th style="width:140px">加价规则快照</th>
              <th style="width:130px">发布时间</th>
              <th style="width:110px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in items" :key="m.id">
              <td>
                <div class="prod-cell">
                  <img :src="m.sourceImage" class="thumb" :alt="m.sourceTitle" />
                  <div>
                    <div class="t">{{ m.sourceTitle }}</div>
                    <div class="caption mono">{{ m.sourceId }}</div>
                  </div>
                </div>
              </td>
              <td>
                <div class="t">{{ m.taobaoTitle }}</div>
                <div class="caption mono">{{ m.taobaoId }}</div>
              </td>
              <td class="caption">{{ m.markupSnapshot }}</td>
              <td class="caption">{{ fmtTime(m.publishedAt) }}</td>
              <td>
                <button class="link-btn" @click="detail = m">详情</button>
                <button class="link-btn danger" style="margin-left:12px" @click="deleting = m">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination :total="total" :page="page" @change="p => { page = p; load() }" />
    </TableState>

    <!-- 详情抽屉 -->
    <DrawerPanel :open="!!detail" title="映射详情" @close="detail = null">
      <template v-if="detail">
        <section class="pair">
          <div class="side card-lite">
            <p class="caption">1688 商品</p>
            <p class="t">{{ detail.sourceTitle }}</p>
            <a :href="detail.sourceUrl" target="_blank" rel="noopener" class="caption mono">{{ detail.sourceUrl }}</a>
          </div>
          <div class="side card-lite">
            <p class="caption">淘宝商品</p>
            <p class="t">{{ detail.taobaoTitle }}</p>
            <p class="caption mono">ID: {{ detail.taobaoId }}</p>
          </div>
        </section>

        <section>
          <h2>SKU 对应关系</h2>
          <table class="table">
            <thead><tr><th>1688 规格</th><th>淘宝规格</th><th style="width:100px">售价</th></tr></thead>
            <tbody>
              <tr v-for="(s, i) in detail.skuMap" :key="i">
                <td>{{ s.source }}</td>
                <td>{{ s.taobao }}</td>
                <td class="mono">{{ fmtPrice(s.price) }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section>
          <button class="link-btn" @click="showRaw = !showRaw">{{ showRaw ? '收起' : '查看' }}原始采集数据</button>
          <pre v-if="showRaw" class="raw mono">{{ JSON.stringify(detail.raw, null, 2) }}</pre>
        </section>
      </template>
    </DrawerPanel>

    <ConfirmDialog
      :open="!!deleting" danger title="删除映射" :busy="busy"
      :message="`确定删除「${deleting?.sourceTitle || ''}」的映射关系吗?仅删除记录,不影响已上架商品。`"
      confirm-text="删除" @cancel="deleting = null" @confirm="doDelete"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '@/api'
import { toast } from '@/store/toast'
import { fmtPrice, fmtTime } from '@/utils/format'
import TableState from '@/components/TableState.vue'
import Pagination from '@/components/Pagination.vue'
import DrawerPanel from '@/components/DrawerPanel.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const items = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const detail = ref(null)
const deleting = ref(null)
const showRaw = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await api.listMappings({ keyword: keyword.value, page: page.value })
    items.value = res.items
    total.value = res.total
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function doDelete() {
  busy.value = true
  try {
    await api.deleteMapping(deleting.value.id)
    toast('映射已删除')
    deleting.value = null
    load()
  } catch (e) {
    toast(e.message || '删除失败', 'error')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.filter-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.search { width: 240px; }
.prod-cell { display: flex; gap: 10px; align-items: center; }
.thumb { width: 44px; height: 44px; border-radius: 4px; object-fit: cover; flex: none; }
.t {
  max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.pair { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 24px; }
.card-lite {
  border: 1px solid var(--border); border-radius: var(--radius-card); padding: 12px;
  display: flex; flex-direction: column; gap: 4px;
}
.card-lite .t { white-space: normal; }
section { margin-bottom: 24px; }
section h2 { margin-bottom: 12px; }
.raw {
  margin-top: 12px; padding: 12px; background: var(--bg-page);
  border-radius: var(--radius-ctl); font-size: 12px; overflow-x: auto;
}
</style>
