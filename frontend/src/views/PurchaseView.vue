<script setup>
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const purchases = ref([])
const selectedId = ref(null)
const detail = ref(null)
const loading = ref(false)

const books = ref([])
const suppliers = ref([])

// 新建 / 编辑草稿共用弹窗
const dialogVisible = ref(false)
const dialogMode = ref('create') // create / edit
const editingId = ref(null)
const saving = ref(false)
const form = ref({ supplierId: null, items: [], originalSupplierId: null, originalSupplierDisabled: false })

const STATUS_TAG = { '草稿': 'info', '已入库': 'success', '已作废': 'danger' }

const enabledSuppliers = computed(() => suppliers.value.filter((s) => s.status === '启用'))
const currentSupplier = computed(() => {
  const id = detail.value?.purchase?.supplierId
  return suppliers.value.find((s) => s.id === id) || detail.value?.supplier || null
})

function supplierOfList(p) {
  return suppliers.value.find((s) => s.id === p.supplierId) || null
}

function supplierDisabled(p) {
  const s = supplierOfList(p)
  // 后端快照兜底（供应商列表尚未加载时）
  return s ? s.status === '停用' : p.supplierStatus === '停用'
}

function supplierName(p) {
  const s = supplierOfList(p)
  return s ? s.name : (p.supplierName || `#${p.supplierId}`)
}

const detailDisabled = computed(() => {
  const s = currentSupplier.value
  return s ? s.status === '停用' : detail.value?.purchase?.supplierStatus === '停用'
})

const detailDisabledReason = computed(() => {
  const s = currentSupplier.value
  return s ? s.disabledReason : detail.value?.purchase?.supplierDisabledReason
})

// 表单内当前选中的供应商（弹窗中展示停用原因）
const formSelectedSupplier = computed(() =>
  suppliers.value.find((s) => s.id === form.value.supplierId) || null)

async function load() {
  loading.value = true
  try {
    const [list] = await Promise.all([api.listPurchases(), loadSuppliers()])
    purchases.value = list
    if (!selectedId.value && purchases.value.length) {
      selectedId.value = purchases.value[0].id
    }
    if (selectedId.value) {
      detail.value = await api.getPurchase(selectedId.value)
    } else {
      detail.value = null
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadSuppliers() {
  try {
    suppliers.value = await api.listSuppliers()
  } catch (e) {
    // 供应商字典加载失败不阻断采购单查看；名称仍有后端快照兜底
    suppliers.value = []
  }
}

async function selectPurchase(id) {
  selectedId.value = id
  try {
    detail.value = await api.getPurchase(id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function bookTitle(id) {
  const b = books.value.find((x) => x.id === id)
  return b ? `${b.title}（库存${b.stock}）` : `#${id}`
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

async function ensureBooks() {
  if (!books.value.length) {
    books.value = await api.listBooks()
  }
}

// ---------------- 新建草稿：只能选启用供应商 ----------------
async function openNew() {
  await ensureBooks()
  dialogMode.value = 'create'
  editingId.value = null
  form.value = { supplierId: null, items: [], originalSupplierId: null, originalSupplierDisabled: false }
  dialogVisible.value = true
}

// ---------------- 编辑草稿：停用供应商的草稿也能补明细，但供应商锁定 ----------------
async function openEdit() {
  await ensureBooks()
  const p = detail.value.purchase
  dialogMode.value = 'edit'
  editingId.value = p.id
  const disabled = detailDisabled.value
  form.value = {
    supplierId: p.supplierId,
    originalSupplierId: p.supplierId,
    originalSupplierDisabled: disabled,
    items: detail.value.items.map((it) => ({ bookId: it.bookId, qty: it.qty, cost: Number(it.cost) }))
  }
  dialogVisible.value = true
}

function addItem() {
  form.value.items.push({ bookId: null, qty: 1, cost: 0 })
}

function removeItem(i) {
  form.value.items.splice(i, 1)
}

const dialogTitle = computed(() =>
  dialogMode.value === 'create' ? '新建进货单（草稿）' : `编辑进货单 #${editingId.value}（草稿）`)

async function submitForm() {
  if (!form.value.supplierId) {
    ElMessage.error('请选择供应商')
    return
  }
  // 前端闭环：新建时禁止提交停用供应商；编辑时仅允许原供应商（停用）或改挂启用供应商
  const sel = formSelectedSupplier.value
  if (dialogMode.value === 'create' && sel && sel.status === '停用') {
    ElMessage.error(`供应商「${sel.name}」已停用，不能新建采购草稿`)
    return
  }
  if (!form.value.items.length) {
    ElMessage.error('进货明细不能为空')
    return
  }
  const payload = {
    supplierId: form.value.supplierId,
    items: form.value.items.map((it) => ({ bookId: it.bookId, qty: it.qty, cost: it.cost }))
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await api.createPurchase(payload)
      ElMessage.success('进货单已创建（草稿）')
    } else {
      await api.updatePurchase(editingId.value, payload)
      ElMessage.success('草稿已更新')
    }
    dialogVisible.value = false
    await load()
    emit('changed')
  } catch (e) {
    // 并发场景下后端会拒绝（如提交瞬间供应商被停用）：原样展示原因，单据无任何变更，可安全重试
    ElMessage.error(e.message)
    await loadSuppliers()
  } finally {
    saving.value = false
  }
}

async function stockIn() {
  try {
    await api.stockInPurchase(selectedId.value)
    ElMessage.success('已入库，库存已增加')
    await load()
    emit('changed')
  } catch (e) {
    // 入库前后端重新校验供应商状态：停用即整单拒绝，状态/库存/明细无半更新。
    // 这里展示明确原因并刷新详情，用户重新启用后可安全重试。
    ElMessage.error(e.message)
    await load()
  }
}

async function voidIt() {
  try {
    await api.voidPurchase(selectedId.value)
    ElMessage.success('已作废')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
    await load()
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <h3 style="margin:0">进货单</h3>
      <el-button type="primary" @click="openNew">新建进货单</el-button>
    </div>

    <div class="layout" v-loading="loading">
      <!-- 左侧列表 -->
      <div class="list">
        <div
          v-for="p in purchases"
          :key="p.id"
          class="row"
          :class="{ on: p.id === selectedId }"
          @click="selectPurchase(p.id)"
        >
          <div class="row-top">
            <span class="pid">进货单 #{{ p.id }}</span>
            <el-tag :type="STATUS_TAG[p.status]" size="small">{{ p.status }}</el-tag>
          </div>
          <div class="row-sub">
            {{ supplierName(p) }} · 总额 ¥{{ (p.total || 0).toFixed(2) }}
          </div>
          <!-- 列表层就把供应商不可用状态暴露出来 -->
          <div v-if="p.status === '草稿' && supplierDisabled(p)" class="row-warn">
            ⚠ 供应商已停用：草稿保留、可补明细，但不能入库
          </div>
        </div>
        <el-empty v-if="!purchases.length" description="暂无进货单" :image-size="60" />
      </div>

      <!-- 右侧明细 -->
      <div class="detail">
        <template v-if="detail">
          <div class="detail-head">
            <span>进货单 #{{ detail.purchase.id }}</span>
            <el-tag :type="STATUS_TAG[detail.purchase.status]" size="small">{{ detail.purchase.status }}</el-tag>
            <span class="muted">供应商：{{ currentSupplier?.name || detail.purchase.supplierName || ('#' + detail.purchase.supplierId) }}</span>
            <el-tag
              v-if="currentSupplier || detail.purchase.supplierStatus"
              :type="detailDisabled ? 'danger' : 'success'"
              size="small"
              effect="plain"
            >供应商{{ detailDisabled ? '已停用' : '启用中' }}</el-tag>
            <span class="muted">总额 ¥{{ (detail.purchase.total || 0).toFixed(2) }}</span>
          </div>

          <!-- 停用闭环：在采购单详情明确展示不可用原因与最近变更时间 -->
          <el-alert
            v-if="detailDisabled"
            class="supplier-alert"
            type="error"
            show-icon
            :closable="false"
            title="该供应商已停用，本单不能入库"
          >
            <template #default>
              <div>停用原因：{{ detailDisabledReason || '（未填写）' }}</div>
              <div>
                最近变更时间：{{ fmt(currentSupplier?.statusChangedAt) }}
                ；原供应商关系与本单明细已保留，可继续补充明细；待供应商重新启用后即可入库。
              </div>
            </template>
          </el-alert>

          <el-table :data="detail.items" border size="small" style="margin-top:10px">
            <el-table-column label="图书" min-width="180">
              <template #default="{ row }">{{ bookTitle(row.bookId) }}</template>
            </el-table-column>
            <el-table-column prop="qty" label="数量" width="80" />
            <el-table-column label="进价" width="100">
              <template #default="{ row }">¥{{ (row.cost || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="小计" width="110">
              <template #default="{ row }">¥{{ ((row.cost || 0) * (row.qty || 0)).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
          <div class="actions">
            <el-button
              type="success"
              :disabled="detail.purchase.status !== '草稿' || detailDisabled"
              @click="stockIn"
            >入库</el-button>
            <el-button
              type="warning"
              plain
              :disabled="detail.purchase.status !== '草稿'"
              @click="openEdit"
            >编辑明细</el-button>
            <el-button
              type="danger"
              plain
              :disabled="detail.purchase.status === '已作废'"
              @click="voidIt"
            >作废</el-button>
            <span v-if="detail.purchase.status === '草稿' && detailDisabled" class="hint">
              入库被锁定：重新启用供应商后恢复
            </span>
          </div>
        </template>
        <el-empty v-else description="请选择左侧进货单" />
      </div>
    </div>

    <!-- 新建 / 编辑草稿弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="660px">
      <el-form label-width="80px">
        <el-form-item label="供应商">
          <div class="supplier-pick">
            <el-select
              v-model="form.supplierId"
              placeholder="选择供应商"
              style="width: 100%"
              :disabled="form.originalSupplierDisabled"
            >
              <!--
                新建：只渲染启用供应商，停用项不可选；
                编辑停用供应商的草稿：select 已锁定，仍需把该停用供应商渲染出来以显示名称。
              -->
              <el-option
                v-for="s in (dialogMode === 'create' ? enabledSuppliers : suppliers)"
                :key="s.id"
                :label="s.status === '停用' ? `${s.name}（已停用）` : s.name"
                :value="s.id"
                :disabled="dialogMode === 'edit' && s.id !== form.originalSupplierId && s.status === '停用'"
              />
            </el-select>
            <el-tag v-if="form.originalSupplierDisabled" type="danger" size="small">
              原供应商已停用，关系保留不可改挂
            </el-tag>
          </div>
          <!-- 选中项的不可用原因闭环 -->
          <el-alert
            v-if="formSelectedSupplier && formSelectedSupplier.status === '停用'"
            class="pick-alert"
            type="error"
            :closable="false"
            show-icon
            :title="`供应商「${formSelectedSupplier.name}」已停用（原因：${formSelectedSupplier.disabledReason || '未填写'}），不能新建挂接它的采购草稿`"
          />
          <div v-if="dialogMode === 'create' && !enabledSuppliers.length" class="empty-tip">
            暂无启用供应商，请先到「供应商」页新增或重新启用。
          </div>
        </el-form-item>
        <el-form-item label="明细">
          <div class="items">
            <div v-for="(it, i) in form.items" :key="i" class="item-row">
              <el-select v-model="it.bookId" placeholder="图书" style="width: 220px" filterable>
                <el-option v-for="b in books" :key="b.id" :label="`${b.title}（库存${b.stock}）`" :value="b.id" />
              </el-select>
              <el-input-number v-model="it.qty" :min="1" controls-position="right" style="width: 110px" />
              <el-input-number v-model="it.cost" :min="0" :precision="2" controls-position="right" style="width: 130px" />
              <el-button link type="danger" @click="removeItem(i)">删</el-button>
            </div>
            <el-button link type="primary" @click="addItem">+ 添加明细</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">
          {{ dialogMode === 'create' ? '创建' : '保存明细' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.layout { display: flex; gap: 16px; align-items: flex-start; }
.list { width: 300px; flex-shrink: 0; display: flex; flex-direction: column; gap: 8px; }
.row { border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; cursor: pointer; background: #fff; }
.row:hover { border-color: var(--primary-soft); }
.row.on { border-color: var(--primary); background: var(--primary-wash); }
.row-top { display: flex; justify-content: space-between; align-items: center; }
.pid { font-weight: 600; }
.row-sub { color: var(--muted); font-size: 12px; margin-top: 4px; }
.row-warn { color: var(--el-color-danger); font-size: 12px; margin-top: 5px; line-height: 1.4; }
.detail { flex: 1; min-width: 0; background: #fff; border: 1px solid var(--line); border-radius: 8px; padding: 14px 16px; }
.detail-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-weight: 600; }
.muted { color: var(--muted); font-weight: 400; font-size: 13px; }
.supplier-alert { margin-top: 10px; }
.actions { margin-top: 14px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { color: var(--el-color-danger); font-size: 12px; }
.items { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.item-row { display: flex; gap: 8px; align-items: center; }
.supplier-pick { display: flex; gap: 10px; align-items: center; width: 100%; }
.pick-alert { margin-top: 8px; width: 100%; }
.empty-tip { color: var(--muted); font-size: 12px; margin-top: 6px; }
</style>
