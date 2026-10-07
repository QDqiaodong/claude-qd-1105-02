<script setup>
import { ref, watch, onMounted } from 'vue'
import router from '../router'
import api from '../api'

// ------------------------------------------------------------------
// 图书详情：只按路由里的主键 id 加载。
// 无论从图书列表还是销售单明细跳入，都用 id 定位，
// 列表的排序 / 分页变化不会导致跳错书。
// 进入时地址栏带着列表的查询条件，「返回列表」原样带回，结果保持一致。
// ------------------------------------------------------------------

const route = router.currentRoute

const book = ref(null)
const loading = ref(false)
const notFound = ref(false)

const STATUS_TAG = { '在售': 'success', '缺货': 'warning', '下架': 'info' }

// 连续切换详情时的竞态防护：只采纳最后一次请求
let reqSeq = 0

async function load(id) {
  const my = ++reqSeq
  loading.value = true
  notFound.value = false
  try {
    const data = await api.getBook(id)
    if (my !== reqSeq) return
    book.value = data
  } catch (e) {
    if (my !== reqSeq) return
    book.value = null
    notFound.value = true
  } finally {
    if (my === reqSeq) loading.value = false
  }
}

function back() {
  // 带着进入时的查询条件 / 分页返回，列表结果与离开前一致
  router.push({ path: '/books', query: route.value.query })
}

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '—'
}

watch(
  () => route.value.params.id,
  (id) => { if (route.value.name === 'book-detail' && id) load(id) }
)

onMounted(() => load(route.value.params.id))
</script>

<template>
  <div v-loading="loading">
    <div class="toolbar">
      <el-button @click="back">← 返回列表</el-button>
      <h3 style="margin:0">图书详情</h3>
    </div>

    <template v-if="book">
      <el-descriptions :column="2" border class="desc">
        <el-descriptions-item label="书名">{{ book.title || '—' }}</el-descriptions-item>
        <el-descriptions-item label="ISBN">
          <span v-if="book.isbn">{{ book.isbn }}</span>
          <span v-else class="missing">未登记（旧数据无 ISBN）</span>
        </el-descriptions-item>
        <el-descriptions-item label="作者">{{ book.author || '—' }}</el-descriptions-item>
        <el-descriptions-item label="定价">
          <span v-if="book.price != null">¥{{ Number(book.price).toFixed(2) }}</span>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="库存">{{ book.stock != null ? book.stock : '—' }}</el-descriptions-item>
        <el-descriptions-item label="销售状态">
          <el-tag v-if="book.status" :type="STATUS_TAG[book.status] || 'info'" size="small">{{ book.status }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="建档时间">{{ fmtTime(book.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="档案编号">#{{ book.id }}</el-descriptions-item>
      </el-descriptions>
    </template>

    <el-empty v-else-if="notFound" description="图书不存在或已删除">
      <el-button type="primary" @click="back">返回列表</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.desc { max-width: 760px; }
.missing { color: var(--muted); }
</style>
