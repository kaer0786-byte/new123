<template>
  <div class="card">
    <div class="filter-row">
      <h2>团队成员</h2>
      <button class="btn btn-primary" @click="createOpen = true">新建成员</button>
    </div>

    <TableState :loading="loading" :error="error" :empty="!members.length" @retry="load">
      <table class="table">
        <thead>
          <tr>
            <th>账号</th>
            <th style="width:120px">角色</th>
            <th style="width:160px">创建时间</th>
            <th style="width:150px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in members" :key="m.id">
            <td class="mono">{{ m.username }}<span v-if="m.username === auth.username" class="caption">(我)</span></td>
            <td><StatusTag :status="m.role" /></td>
            <td class="caption">{{ fmtTime(m.createdAt) }}</td>
            <td>
              <button class="link-btn" @click="toast('重置链接已生成(演示)')">重置密码</button>
              <button
                class="link-btn danger" style="margin-left:12px"
                :disabled="m.username === auth.username"
                :title="m.username === auth.username ? '不可删除自己' : ''"
                @click="deleting = m"
              >删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </TableState>

    <!-- 新建成员弹窗 -->
    <Teleport to="body">
      <div v-if="createOpen" class="overlay" @click.self="createOpen = false">
        <form class="dialog" @submit.prevent="doCreate">
          <h2>新建成员</h2>
          <div class="field" style="margin-top:16px">
            <label for="nu">账号</label>
            <input id="nu" class="input" v-model.trim="newMember.username" placeholder="登录账号" />
          </div>
          <div class="field">
            <label for="np">初始密码</label>
            <input id="np" class="input" v-model="newMember.password" placeholder="至少 6 位" />
          </div>
          <div class="field">
            <label>角色</label>
            <div class="radio-row">
              <label><input type="radio" value="operator" v-model="newMember.role" /> 操作员</label>
              <label><input type="radio" value="admin" v-model="newMember.role" /> 管理员</label>
            </div>
          </div>
          <div class="actions">
            <button type="button" class="btn btn-secondary" @click="createOpen = false">取消</button>
            <button class="btn btn-primary" :disabled="busy || !newMember.username || newMember.password.length < 6">
              <span v-if="busy" class="spinner"></span>创建
            </button>
          </div>
        </form>
      </div>
    </Teleport>

    <ConfirmDialog
      :open="!!deleting" danger title="删除成员" :busy="busy"
      :message="`确定删除成员「${deleting?.username || ''}」吗?其登录权限将立即失效。`"
      confirm-text="删除" @cancel="deleting = null" @confirm="doDelete"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '@/api'
import { auth } from '@/store/auth'
import { toast } from '@/store/toast'
import { fmtTime } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import TableState from '@/components/TableState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const members = ref([])
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const createOpen = ref(false)
const deleting = ref(null)
const newMember = ref({ username: '', password: '', role: 'operator' })

async function load() {
  loading.value = true
  error.value = ''
  try {
    members.value = await api.listMembers()
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function doCreate() {
  busy.value = true
  try {
    await api.createMember({ ...newMember.value })
    toast('成员已创建')
    createOpen.value = false
    newMember.value = { username: '', password: '', role: 'operator' }
    load()
  } catch (e) {
    toast(e.message || '创建失败', 'error')
  } finally {
    busy.value = false
  }
}

async function doDelete() {
  busy.value = true
  try {
    await api.deleteMember(deleting.value.id)
    toast('成员已删除')
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
.overlay {
  position: fixed; inset: 0; background: var(--overlay); z-index: 900;
  display: flex; align-items: center; justify-content: center;
}
.dialog {
  width: 420px; background: var(--bg-card); border-radius: var(--radius-card);
  box-shadow: var(--shadow-modal); padding: 24px;
}
.radio-row { display: flex; gap: 24px; }
.radio-row label { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.actions { display: flex; justify-content: flex-end; gap: 12px; }
</style>
