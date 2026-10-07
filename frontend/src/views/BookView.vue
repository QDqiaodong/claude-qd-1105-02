<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import router from '../router'
import api from '../api'

const emit = defineEmits(['changed'])

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const isbn = ref('')
const statusFilter = ref('')
const loading = ref(false)

const STATUS_OPTIONS = ['在售', '缺货', '下架']

const showNew = ref(false)
const newRow = ref({ isbn: '', title: '', author: '', price: 0, stock: 0, status: '在售' })

// ------------------------------------------------------------------
// 竞态防护：高频连续查询时，每次请求自增序号，只有最后一次请求的结果
// 会被采纳；较慢返回的前一次响应直接丢弃，不会覆盖更新的结果。
// ------------------------------------------------------------------
let reqSeq = 0

async function load() {
  const my = ++reqSeq
  loading.value = true
  try {
    const data = await api.pageBooks({
      status: statusFilter.value || '',
      keyword: keyword.value || '',
      isbn: isbn.value || '',
      page: page.value,
      size: size.value
    })
    if (my !== reqSeq) return // 已有更新的查询在途/完成，丢弃这份过期结果
    rows.value = data.list
    total.value = data.total
  } catch (e) {
    if (my === reqSeq) ElMessage.error(e.message)
  } finally {
    if (my === reqSeq) loading.value = false
  }
}

// 查询条件 + 分页写入地址栏：刷新、从详情页返回后结果保持一致
function currentQuery() {
  const q = {}
  if (keyword.value) q.keyword = keyword.value
  if (statusFilter.value) q.status = statusFilter.value
  if (isbn.value) q.isbn = isbn.value
  if (page.value > 1) q.page = String(page.value)
  if (size.value !== 10) q.size = String(size.value)
  return q
}

watch([keyword, statusFilter, isbn, page, size], () => {
  router.replace({ path: '/books', query: currentQuery() }).catch(() => {})
})

// 应用筛选：ISBN 只输入空白时不当成任意匹配，明确提示后放弃本次查询
function applyFilters() {
  if (isbn.value && !isbn.value.trim()) {
    ElMessage.warning('ISBN 不能只输入空白字符，请输入完整或部分 ISBN')
    return
  }
  isbn.value = (isbn.value || '').trim()
  keyword.value = (keyword.value || '').trim()
  page.value = 1
  load()
}

function onSizeChange() {
  page.value = 1
  load()
}

// 详情跳转：用主键 id 定位，与列表排序 / 分页无关，绝不会跳到别的书；
// 地址栏带上当前查询条件，详情页「返回列表」原样带回
function goDetail(row) {
  router.push({ path: `/books/${row.id}`, query: currentQuery() })
}

// 当前页内 ISBN 重复值检测（旧数据可能因历史原因存在重复），给出明确提示
const duplicateIsbns = computed(() => {
  const count = {}
  for (const r of rows.value) {
    if (r.isbn) count[r.isbn] = (count[r.isbn] || 0) + 1
  }
  return Object.entries(count).filter(([, n]) => n > 1).map(([v, n]) => ({ isbn: v, count: n }))
})

const emptyText = computed(() => {
  if (isbn.value) return `未找到 ISBN 含「${isbn.value}」的图书`
  if (keyword.value || statusFilter.value) return '没有符合条件的图书'
  return '暂无图书数据'
})

async function saveRow(row) {
  try {
    await api.updateBook(row.id, {
      isbn: row.isbn,
      title: row.title,
      author: row.author,
      price: row.price,
      stock: row.stock,
      status: row.status
    })
    ElMessage.success('已保存')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openNew() {
  newRow.value = { isbn: '', title: '', author: '', price: 0, stock: 0, status: '在售' }
  showNew.value = true
}

async function submitNew() {
  try {
    await api.createBook({ ...newRow.value })
    ElMessage.success('新增成功')
    showNew.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function statusType(s) {
  if (s === '在售') return 'success'
  if (s === '缺货') return 'warning'
  return 'info'
}

onMounted(() => {
  // 从地址栏恢复查询条件与分页（刷新 / 从详情返回时保持一致）
  const q = router.currentRoute.value.query
  if (typeof q.keyword === 'string') keyword.value = q.keyword
  if (typeof q.isbn === 'string') isbn.value = q.isbn
  if (typeof q.status === 'string') statusFilter.value = q.status
  const p = parseInt(q.page, 10)
  if (p > 0) page.value = p
  const s = parseInt(q.size, 10)
  if (s > 0) size.value = s
  load()
})
</script>

<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索书名 / 作者 / ISBN" clearable style="width: 220px"
        @keyup.enter="applyFilters" @clear="applyFilters" />
      <el-input v-model="isbn" placeholder="完整或部分 ISBN" clearable style="width: 180px"
        @keyup.enter="applyFilters" @clear="applyFilters" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px" @change="applyFilters">
        <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" @click="applyFilters">查询</el-button>
      <el-button @click="openNew">新增图书</el-button>
    </div>

    <el-alert
      v-for="d in duplicateIsbns"
      :key="d.isbn"
      type="warning"
      :closable="false"
      show-icon
      class="dup-alert"
      :title="`ISBN「${d.isbn}」在当前结果中有 ${d.count} 条重复档案，请核对后再操作`"
    />

    <el-table :data="rows" v-loading="loading" border stripe style="margin-top: 12px">
      <el-table-column prop="isbn" label="ISBN" min-width="150">
        <template #default="{ row }">
          <el-input v-model="row.isbn" size="small" placeholder="未登记" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="书名" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.title" size="small" />
        </template>
      </el-table-column>
      <el-table-column prop="author" label="作者" min-width="120">
        <template #default="{ row }">
          <el-input v-model="row.author" size="small" />
        </template>
      </el-table-column>
      <el-table-column prop="price" label="定价" width="130">
        <template #default="{ row }">
          <el-input-number v-model="row.price" :min="0" :precision="2" :step="1" size="small" controls-position="right" style="width: 110px" />
        </template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="130">
        <template #default="{ row }">
          <el-input-number v-model="row.stock" :min="0" :step="1" size="small" controls-position="right" style="width: 110px" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-select v-model="row.status" size="small" style="width: 90px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="saveRow(row)">保存</el-button>
          <el-button type="primary" link size="small" @click="goDetail(row)">详情</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="emptyText" :image-size="70" />
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      background
      class="pager"
      @current-change="load"
      @size-change="onSizeChange"
    />

    <!-- 行内新增表单（非弹窗） -->
    <el-card v-if="showNew" class="new-card" shadow="never">
      <template #header>新增图书</template>
      <div class="new-form">
        <el-input v-model="newRow.isbn" placeholder="ISBN" style="width: 160px" />
        <el-input v-model="newRow.title" placeholder="书名" style="width: 160px" />
        <el-input v-model="newRow.author" placeholder="作者" style="width: 120px" />
        <el-input-number v-model="newRow.price" :min="0" :precision="2" placeholder="定价" style="width: 120px" controls-position="right" />
        <el-input-number v-model="newRow.stock" :min="0" placeholder="库存" style="width: 110px" controls-position="right" />
        <el-button type="primary" @click="submitNew">确定</el-button>
        <el-button @click="showNew = false">取消</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.dup-alert { margin-top: 10px; }
.pager { margin-top: 12px; justify-content: flex-end; }
.new-card { margin-top: 16px; }
.new-form { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
</style>
