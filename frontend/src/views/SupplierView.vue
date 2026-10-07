<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const suppliers = ref([])
const keyword = ref('')
const statusFilter = ref('')
const loading = ref(false)
const editingId = ref(null)
const draft = ref({ name: '', contact: '', phone: '', status: '启用' })

const showNew = ref(false)
const newRow = ref({ name: '', contact: '', phone: '' })

// 当前查看变更流水的供应商
const logTarget = ref(null)
const logs = ref([])
const logLoading = ref(false)

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  loading.value = true
  try {
    suppliers.value = await api.listSuppliers(keyword.value || '', statusFilter.value || '')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function startEdit(s) {
  editingId.value = s.id
  draft.value = { name: s.name, contact: s.contact, phone: s.phone, status: s.status }
}

async function saveEdit(s) {
  try {
    await api.updateSupplier(s.id, { name: draft.value.name, contact: draft.value.contact, phone: draft.value.phone })
    ElMessage.success('已保存')
    editingId.value = null
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function cancelEdit() {
  editingId.value = null
}

function openNew() {
  newRow.value = { name: '', contact: '', phone: '' }
  showNew.value = true
}

async function submitNew() {
  try {
    await api.createSupplier({ ...newRow.value })
    ElMessage.success('新增成功（默认启用）')
    showNew.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------- 停用 / 启用 ----------------
async function disable(s) {
  let reason
  try {
    const r = await ElMessageBox.prompt(
      `停用后，将不能为供应商「${s.name}」新建采购草稿，其名下已有草稿也无法入库（草稿与明细仍保留）。请填写停用原因：`,
      `停用供应商 #${s.id}`,
      {
        confirmButtonText: '确认停用',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：合同到期 / 质量问题 / 暂停合作（必填）',
        inputValidator: (v) => (v && v.trim() ? true : '停用原因不能为空'),
        type: 'warning'
      }
    )
    reason = r.value
  } catch (e) {
    return // 用户取消
  }
  try {
    await api.changeSupplierStatus(s.id, '停用', reason.trim())
    ElMessage.success('已停用')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function enable(s) {
  try {
    await ElMessageBox.confirm(
      `重新启用「${s.name}」后，其名下原采购草稿可继续入库，也可新建挂接它的采购单。`,
      `重新启用供应商 #${s.id}`,
      { confirmButtonText: '确认启用', cancelButtonText: '取消', type: 'info' }
    )
  } catch (e) {
    return
  }
  try {
    await api.changeSupplierStatus(s.id, '启用', null)
    ElMessage.success('已重新启用')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------- 变更流水 ----------------
async function showLogs(s) {
  logTarget.value = s
  logs.value = []
  logLoading.value = true
  try {
    logs.value = await api.supplierStatusLogs(s.id)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    logLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索供应商名" clearable style="width: 200px"
        @keyup.enter="load" @clear="load" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px" @change="load">
        <el-option label="启用" value="启用" />
        <el-option label="停用" value="停用" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="openNew">新增供应商</el-button>
    </div>

    <div v-loading="loading" class="grid">
      <el-card v-for="s in suppliers" :key="s.id" class="card" :class="{ off: s.status === '停用' }" shadow="hover">
        <template #header>
          <div class="card-head">
            <span class="name">{{ s.name }}</span>
            <el-tag :type="s.status === '停用' ? 'danger' : 'success'" size="small" effect="dark">
              {{ s.status || '启用' }}
            </el-tag>
          </div>
        </template>

        <div v-if="editingId === s.id" class="form">
          <el-input v-model="draft.name" size="small" placeholder="供应商名" />
          <el-input v-model="draft.contact" size="small" placeholder="联系人" />
          <el-input v-model="draft.phone" size="small" placeholder="手机" />
          <div class="ops">
            <el-button type="primary" size="small" @click="saveEdit(s)">保存</el-button>
            <el-button size="small" @click="cancelEdit">取消</el-button>
          </div>
        </div>
        <div v-else class="info">
          <div><span class="k">编号</span>#{{ s.id }}</div>
          <div><span class="k">联系人</span>{{ s.contact || '—' }}</div>
          <div><span class="k">手机</span>{{ s.phone || '—' }}</div>
          <!-- 状态 / 停用原因 / 最近变更时间：可追踪闭环 -->
          <div class="status-row">
            <span class="k">状态</span>
            <el-tag :type="s.status === '停用' ? 'danger' : 'success'" size="small">
              {{ s.status || '启用' }}
            </el-tag>
          </div>
          <div v-if="s.status === '停用'" class="reason">
            <el-alert
              :title="`停用原因：${s.disabledReason || '（未填写）'}`"
              type="error" :closable="false" show-icon
            />
          </div>
          <div class="changed">
            <span class="k">变更于</span>{{ fmt(s.statusChangedAt) }}
            <el-button link type="primary" size="small" @click="showLogs(s)">变更记录</el-button>
          </div>
          <div class="ops">
            <el-button link type="primary" size="small" @click="startEdit(s)">编辑</el-button>
            <el-button v-if="s.status !== '停用'" link type="danger" size="small" @click="disable(s)">停用</el-button>
            <el-button v-else link type="success" size="small" @click="enable(s)">重新启用</el-button>
          </div>
        </div>
      </el-card>
      <el-empty v-if="!loading && !suppliers.length" description="没有符合条件的供应商" />
    </div>

    <el-card v-if="showNew" class="new-card" shadow="never">
      <template #header>新增供应商（默认「启用」）</template>
      <div class="form">
        <el-input v-model="newRow.name" placeholder="供应商名" style="width: 180px" />
        <el-input v-model="newRow.contact" placeholder="联系人" style="width: 140px" />
        <el-input v-model="newRow.phone" placeholder="手机" style="width: 160px" />
        <el-button type="primary" @click="submitNew">确定</el-button>
        <el-button @click="showNew = false">取消</el-button>
      </div>
    </el-card>

    <!-- 状态变更流水 -->
    <el-drawer v-model="logTarget" :title="logTarget ? `状态变更记录 · ${logTarget.name}` : ''" size="420px">
      <template v-if="logTarget">
        <el-timeline v-loading="logLoading">
          <el-timeline-item
            v-for="l in logs"
            :key="l.id"
            :type="l.toStatus === '停用' ? 'danger' : 'success'"
            :timestamp="fmt(l.changedAt)"
            placement="top"
          >
            <div class="log-line">
              <el-tag size="small" :type="l.fromStatus === '停用' ? 'danger' : 'info'">
                {{ l.fromStatus || '—' }}
              </el-tag>
              <span class="arrow">→</span>
              <el-tag size="small" :type="l.toStatus === '停用' ? 'danger' : 'success'">
                {{ l.toStatus }}
              </el-tag>
            </div>
            <div v-if="l.reason" class="log-reason">原因：{{ l.reason }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="!logLoading && !logs.length" description="暂无变更记录" :image-size="60" />
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 16px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; }
.card.off { border-color: var(--el-color-danger-light-5); }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.name { font-weight: 600; font-size: 15px; }
.sid { color: var(--muted); font-size: 12px; }
.info { display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
.info .k { display: inline-block; width: 48px; color: var(--muted); }
.status-row { display: flex; align-items: center; gap: 6px; }
.status-row .k { flex-shrink: 0; }
.reason :deep(.el-alert) { padding: 4px 8px; }
.reason :deep(.el-alert__title) { font-size: 12px; line-height: 1.4; }
.changed { display: flex; align-items: center; gap: 4px; color: var(--muted); font-size: 12px; flex-wrap: wrap; }
.form { display: flex; flex-direction: column; gap: 8px; }
.ops { display: flex; gap: 8px; align-items: center; }
.new-card { margin-top: 16px; }
.log-line { display: flex; align-items: center; gap: 8px; }
.log-line .arrow { color: var(--muted); }
.log-reason { margin-top: 4px; font-size: 13px; color: var(--ink); }
</style>
