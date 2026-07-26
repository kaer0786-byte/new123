<template>
  <DrawerPanel :open="!!productId" title="商品详情 / 编辑" :width="640" @close="$emit('close')">
    <div v-if="loading" class="center"><span class="spinner dark"></span></div>

    <template v-else-if="p">
      <!-- 主图轮播(缩略切换) -->
      <div class="gallery">
        <img :src="gallery[galleryIdx]" class="main-img" :alt="p.title" />
        <div class="thumbs">
          <img
            v-for="(img, i) in gallery" :key="i" :src="img"
            class="thumb" :class="{ active: i === galleryIdx }"
            :alt="`图${i + 1}`" @click="galleryIdx = i"
          />
        </div>
      </div>

      <!-- 标题区 -->
      <section>
        <h2>标题</h2>
        <p class="caption origin">原标题:{{ p.title }}</p>
        <label v-for="(t, i) in p.optimizedTitles" :key="i" class="title-option">
          <input type="radio" :value="t" v-model="form.selectedTitle" name="title" />
          <span>{{ t }} <em class="caption">AI 候选 {{ i + 1 }}</em></span>
        </label>
        <label class="title-option">
          <input type="radio" :value="customTitle" v-model="form.selectedTitle" name="title" :disabled="!customTitle" />
          <input
            class="input" v-model.trim="customTitle" placeholder="自定义标题…"
            @input="form.selectedTitle = customTitle"
          />
        </label>
      </section>

      <!-- 价格区 -->
      <section>
        <h2>价格</h2>
        <div class="price-row">
          <div class="field" style="margin:0">
            <label>采集价(只读)</label>
            <input class="input" :value="fmtPrice(p.costPrice)" disabled />
          </div>
          <div class="field" style="margin:0">
            <label for="price">售价</label>
            <input id="price" class="input" type="number" step="0.01" min="0" v-model.number="form.price" />
          </div>
        </div>
        <p class="caption">按当前加价规则的建议售价约 {{ fmtPrice(p.costPrice * 1.3) }}(可在配置中心调整规则)</p>
      </section>

      <!-- SKU -->
      <section>
        <h2>SKU 规格</h2>
        <table class="table">
          <thead><tr><th>规格</th><th style="width:130px">价格</th><th style="width:110px">库存</th></tr></thead>
          <tbody>
            <tr v-for="(sku, i) in form.skus" :key="i">
              <td>{{ sku.spec }}</td>
              <td><input class="input" type="number" step="0.01" min="0" v-model.number="sku.price" :aria-label="`${sku.spec} 价格`" /></td>
              <td><input class="input" type="number" min="0" v-model.number="sku.stock" :aria-label="`${sku.spec} 库存`" /></td>
            </tr>
          </tbody>
        </table>
      </section>

      <section>
        <h2>商品链接</h2>
        <a :href="p.sourceUrl" target="_blank" rel="noopener" class="mono caption">{{ p.sourceUrl }}</a>
      </section>
    </template>

    <template #footer>
      <button class="link-btn danger" @click="confirmDelete = true">删除商品</button>
      <div style="display:flex;gap:12px">
        <button class="btn btn-secondary" :disabled="busy" @click="save(false)">
          <span v-if="busy" class="spinner dark"></span>保存
        </button>
        <button class="btn btn-primary" :disabled="busy" @click="save(true)">保存并标记待发布</button>
      </div>
    </template>
  </DrawerPanel>

  <ConfirmDialog
    :open="confirmDelete" danger title="删除商品" :busy="busy"
    :message="`确定删除商品「${p?.title || ''}」吗?删除后不可恢复。`"
    confirm-text="删除" @cancel="confirmDelete = false" @confirm="doDelete"
  />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import { toast } from '@/store/toast'
import { fmtPrice } from '@/utils/format'
import DrawerPanel from '@/components/DrawerPanel.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const props = defineProps({ productId: { type: Number, default: null } })
const emit = defineEmits(['close', 'saved'])

const p = ref(null)
const loading = ref(false)
const busy = ref(false)
const galleryIdx = ref(0)
const customTitle = ref('')
const confirmDelete = ref(false)
const form = ref({ selectedTitle: '', price: 0, skus: [] })

const gallery = computed(() => (p.value ? [p.value.image, ...p.value.detailImages] : []))

watch(() => props.productId, async id => {
  if (!id) return
  loading.value = true
  galleryIdx.value = 0
  customTitle.value = ''
  try {
    p.value = await api.getProduct(id)
    form.value = {
      selectedTitle: p.value.selectedTitle,
      price: p.value.price,
      skus: p.value.skus.map(s => ({ ...s }))
    }
  } catch (e) {
    toast(e.message || '加载失败', 'error')
    emit('close')
  } finally {
    loading.value = false
  }
})

async function save(markPending) {
  busy.value = true
  try {
    await api.updateProduct(props.productId, {
      selectedTitle: form.value.selectedTitle,
      price: form.value.price,
      skus: form.value.skus,
      ...(markPending ? { status: 'pending' } : {})
    })
    toast(markPending ? '已保存并标记待发布,App 将同步执行' : '已保存')
    emit('saved')
    emit('close')
  } catch (e) {
    toast(e.message || '保存失败', 'error')
  } finally {
    busy.value = false
  }
}

async function doDelete() {
  busy.value = true
  try {
    await api.deleteProducts([props.productId])
    toast('已删除')
    confirmDelete.value = false
    emit('saved')
    emit('close')
  } catch (e) {
    toast(e.message || '删除失败', 'error')
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.center { display: flex; justify-content: center; padding: 48px 0; }
section { margin-bottom: 24px; }
section h2 { margin-bottom: 12px; }
.gallery { margin-bottom: 24px; }
.main-img { width: 100%; max-height: 280px; object-fit: contain; background: var(--bg-page); border-radius: var(--radius-card); }
.thumbs { display: flex; gap: 8px; margin-top: 8px; }
.thumb {
  width: 56px; height: 56px; border-radius: 4px; object-fit: cover;
  cursor: pointer; border: 2px solid transparent;
}
.thumb.active { border-color: var(--primary); }
.origin { margin-bottom: 8px; }
.title-option {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 0; cursor: pointer;
}
.title-option em { font-style: normal; margin-left: 6px; }
.price-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 8px; }
</style>
