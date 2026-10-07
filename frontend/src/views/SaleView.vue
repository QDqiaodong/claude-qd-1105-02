<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import router from '../router'
import api from '../api'

const emit = defineEmits(['changed'])

const sales = ref([])
const selectedId = ref(null)
const detail = ref(null)
const loading = ref(false)

// 结账 / 退款进行中标志：重复点击、刷新重试期间按钮保持禁用，请求只可能发出一次
const actioning = ref(false)

const books = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const form = ref({ items: [] })

const STATUS_TAG = { '草稿': 'info', '已结账': 'success', '已退款': 'warning' }

// 明细加载竞态防护：快速连点销售单时，只采纳最后一次选择的明细结果
let detailSeq = 0

async function loadDetail(id) {
  const my = ++detailSeq
  try {
    const d = await api.getSale(id)
    if (my !== detailSeq) return // 已选中其他单，丢弃过期结果
    detail.value = d
  } catch (e) {
    if (my === detailSeq) ElMessage.error(e.message)
  }
}

async function load() {
  loading.value = true
  try {
    sales.value = await api.listSales()
    if (!selectedId.value && sales.value.length) {
      selectedId.value = sales.value[0].id
    }
    if (selectedId.value) {
      await loadDetail(selectedId.value)
    } else {
      detail.value = null
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function selectSale(id) {
  selectedId.value = id
  await loadDetail(id)
}

// 从销售明细跳入图书详情：按图书主键定位，与图书列表的排序 / 分页无关，
// 必定落到同一本书；图书档案缺失（旧数据）时明确提示、不跳转
function goBook(row) {
  if (!row.book) {
    ElMessage.warning('该明细的图书档案已缺失，无法查看详情')
    return
  }
  router.push(`/books/${row.bookId}`)
}

function fmtPrice(p) {
  return p != null ? `¥${Number(p).toFixed(2)}` : '—'
}

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

// 单价取结账快照（bookPrice），档案缺失 / 改动后仍按成交单价展示
function rowPrice(row) {
  return row.bookPrice != null ? Number(row.bookPrice) : null
}

function fmtSubtotal(row) {
  const price = rowPrice(row)
  return price != null ? `¥${(price * (row.qty || 0)).toFixed(2)}` : '—'
}

async function openNew() {
  books.value = await api.listBooks('在售')
  form.value = { items: [] }
  dialogVisible.value = true
}

function addItem() {
  form.value.items.push({ bookId: null, qty: 1 })
}
function removeItem(i) {
  form.value.items.splice(i, 1)
}

async function submitNew() {
  saving.value = true
  try {
    await api.createSale({
      items: form.value.items.map((it) => ({ bookId: it.bookId, qty: it.qty }))
    })
    ElMessage.success('销售单已创建（草稿）')
    dialogVisible.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function checkout() {
  if (actioning.value) return
  actioning.value = true
  try {
    await api.checkoutSale(selectedId.value)
    ElMessage.success('已结账，库存已扣减')
    await load()
    emit('changed')
  } catch (e) {
    // 并发冲突 / 库存被其他销售单占用时后端整单拒绝、无半更新；
    // 立即按最新状态刷新，列表与按钮与后端对齐后用户可安全重试
    ElMessage.error(e.message)
    await load()
  } finally {
    actioning.value = false
  }
}

async function refund() {
  if (actioning.value) return
  actioning.value = true
  try {
    const { message } = await api.refundSale(selectedId.value)
    await load()
    if (message && message.includes('档案已缺失')) {
      // 含「档案缺失」明细的退款：库存按可回滚部分整单完成，用警告色突出提示
      ElMessage.warning(message, { duration: 8000 })
    } else {
      ElMessage.success(message || '已退款，库存已回滚')
    }
    emit('changed')
  } catch (e) {
    // 并发退款的后到者、锁冲突等：后端要么已由另一操作整单完成、要么整单未动；
    // 这里必须重新拉取列表与详情，避免页面停留在「已结账可退款」的过期状态
    ElMessage.error(e.message)
    await load()
  } finally {
    actioning.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <h3 style="margin:0">销售单</h3>
      <el-button type="primary" @click="openNew">新建销售单</el-button>
    </div>

    <div class="layout" v-loading="loading">
      <!-- 左侧列表 -->
      <div class="list">
        <div
          v-for="s in sales"
          :key="s.id"
          class="row"
          :class="{ on: s.id === selectedId }"
          @click="selectSale(s.id)"
        >
          <div class="row-top">
            <span class="pid">销售单 #{{ s.id }}</span>
            <el-tag :type="STATUS_TAG[s.status]" size="small">{{ s.status }}</el-tag>
          </div>
          <div class="row-sub">总额 ¥{{ (s.total || 0).toFixed(2) }}</div>
          <div v-if="s.status === '已退款'" class="row-refunded">
            退款时间 {{ fmtTime(s.refundedAt) }}
          </div>
        </div>
        <el-empty v-if="!sales.length" description="暂无销售单" :image-size="60" />
      </div>

      <!-- 右侧明细 -->
      <div class="detail">
        <template v-if="detail">
          <div class="detail-head">
            <span>销售单 #{{ detail.sale.id }}</span>
            <el-tag :type="STATUS_TAG[detail.sale.status]" size="small">{{ detail.sale.status }}</el-tag>
            <span class="muted">总额 ¥{{ (detail.sale.total || 0).toFixed(2) }}</span>
            <span v-if="detail.sale.status === '已退款'" class="muted">
              退款时间：{{ fmtTime(detail.sale.refundedAt) }}
            </span>
          </div>

          <!-- 明细图书档案缺失：保留结账快照并给出明确提示，不影响退款整单完成 -->
          <el-alert
            v-if="detail.hasMissingBook"
            class="missing-alert"
            type="warning"
            show-icon
            :closable="false"
            :title="detail.sale.refundNote || '该单含图书档案缺失的明细，已按结账时快照保留原内容'"
          />

          <el-table :data="detail.items" border size="small" style="margin-top:10px">
            <el-table-column label="图书" min-width="200">
              <template #default="{ row }">
                <template v-if="!row.bookMissing">
                  <div>{{ row.book.title }}</div>
                  <div class="book-sub">ISBN {{ row.book.isbn || '未登记' }}</div>
                </template>
                <template v-else>
                  <div>
                    {{ row.bookTitle || '未命名图书' }}
                    <el-tag type="warning" size="small" effect="plain">档案缺失·快照</el-tag>
                  </div>
                  <div class="book-sub">ISBN {{ row.bookIsbn || '未登记' }}（原档案 #{{ row.bookId }} 已不存在）</div>
                </template>
              </template>
            </el-table-column>
            <el-table-column prop="qty" label="数量" width="80" />
            <el-table-column label="单价" width="100">
              <template #default="{ row }">{{ fmtPrice(row.bookPrice) }}</template>
            </el-table-column>
            <el-table-column label="小计" width="110">
              <template #default="{ row }">{{ fmtSubtotal(row) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="primary" link size="small" :disabled="!!row.bookMissing" @click="goBook(row)">查看图书</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="actions">
            <el-button
              type="success"
              :loading="actioning"
              :disabled="detail.sale.status !== '草稿' || actioning"
              @click="checkout"
            >结账</el-button>
            <el-button
              type="warning"
              :loading="actioning"
              :disabled="detail.sale.status !== '已结账' || actioning"
              @click="refund"
            >退款</el-button>
            <span v-if="detail.sale.status === '已退款'" class="hint">
              已退款（{{ fmtTime(detail.sale.refundedAt) }}），库存已恢复，不可重复退款
            </span>
            <span v-else-if="detail.sale.status === '草稿'" class="hint">
              草稿未结账，暂不能退款
            </span>
          </div>
        </template>
        <el-empty v-else description="请选择左侧销售单" />
      </div>
    </div>

    <!-- 新建弹窗 -->
    <el-dialog v-model="dialogVisible" title="新建销售单（草稿）" width="620px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
        title="仅可添加「在售」图书；结账时若库存不足将整单失败。" />
      <div class="items">
        <div v-for="(it, i) in form.items" :key="i" class="item-row">
          <el-select v-model="it.bookId" placeholder="图书（仅限在售）" style="width: 320px" filterable>
            <el-option v-for="b in books" :key="b.id" :label="`${b.title} ¥${b.price}（库存${b.stock}）`" :value="b.id" />
          </el-select>
          <el-input-number v-model="it.qty" :min="1" controls-position="right" style="width: 120px" />
          <el-button link type="danger" @click="removeItem(i)">删</el-button>
        </div>
        <el-button link type="primary" @click="addItem">+ 添加明细</el-button>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitNew">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.layout { display: flex; gap: 16px; align-items: flex-start; }
.list { width: 280px; flex-shrink: 0; display: flex; flex-direction: column; gap: 8px; }
.row { border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; cursor: pointer; background: #fff; }
.row:hover { border-color: var(--primary-soft); }
.row.on { border-color: var(--primary); background: var(--primary-wash); }
.row-top { display: flex; justify-content: space-between; align-items: center; }
.pid { font-weight: 600; }
.row-sub { color: var(--muted); font-size: 12px; margin-top: 4px; }
.row-refunded { color: var(--el-color-warning); font-size: 12px; margin-top: 4px; }
.detail { flex: 1; min-width: 0; background: #fff; border: 1px solid var(--line); border-radius: 8px; padding: 14px 16px; }
.detail-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-weight: 600; }
.muted { color: var(--muted); font-weight: 400; font-size: 13px; }
.book-sub { color: var(--muted); font-size: 12px; margin-top: 2px; }
.missing-alert { margin-top: 10px; }
.actions { margin-top: 14px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { color: var(--muted); font-size: 12px; }
.items { display: flex; flex-direction: column; gap: 8px; }
.item-row { display: flex; gap: 8px; align-items: center; }
</style>
