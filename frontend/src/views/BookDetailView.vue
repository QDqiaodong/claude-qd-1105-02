<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import router from '../router'
import api from '../api'

// ------------------------------------------------------------------
// 图书详情：只按路由里的主键 id 加载。
// 无论从图书列表还是销售单明细跳入，都用 id 定位，
// 列表的排序 / 分页变化不会导致跳错书。
// 进入时地址栏带着列表的查询条件，「返回列表」原样带回，结果保持一致。
// 下方库存流水逐笔记录销售结账扣减 / 退款回滚 / 进货入库 / 入库单作废，
// 财务库存与销售记录可逐笔核对。
// ------------------------------------------------------------------

const route = router.currentRoute

const book = ref(null)
const ledgers = ref([])
const loading = ref(false)
const notFound = ref(false)

const STATUS_TAG = { '在售': 'success', '缺货': 'warning', '下架': 'info' }
const BIZ_TAG = {
  '销售结账': 'danger',
  '销售退款': 'success',
  '进货入库': 'primary',
  '进货作废': 'warning'
}

// 连续切换详情时的竞态防护：只采纳最后一次请求
let reqSeq = 0

async function load(id) {
  const my = ++reqSeq
  loading.value = true
  notFound.value = false
  ledgers.value = []
  try {
    const [data, ledgerData] = await Promise.all([
      api.getBook(id),
      api.bookStockLedgers(id)
    ])
    if (my !== reqSeq) return
    book.value = data
    ledgers.value = ledgerData
  } catch (e) {
    if (my !== reqSeq) return
    book.value = null
    notFound.value = true
    ElMessage.error(e.message)
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

function changeText(qty) {
  return qty > 0 ? `+${qty}` : String(qty)
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

      <div class="ledger-head">
        <h4 style="margin:0">库存流水</h4>
        <span class="missing">每笔结账扣减 / 退款回滚 / 入库 / 作废都在此留痕，「变更后库存」应与上方库存连续对得上</span>
      </div>
      <el-table :data="ledgers" border size="small" class="ledger">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="BIZ_TAG[row.bizType] || 'info'" size="small">{{ row.bizType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联单据" width="130">
          <template #default="{ row }">
            <span v-if="row.bizType.indexOf('销售') === 0">销售单 #{{ row.bizId }}</span>
            <span v-else>进货单 #{{ row.bizId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存变化" width="100">
          <template #default="{ row }">
            <span :class="row.changeQty > 0 ? 'qty-plus' : 'qty-minus'">{{ changeText(row.changeQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stockAfter" label="变更后库存" width="110" />
        <el-table-column prop="remark" label="备注" min-width="220" />
        <template #empty>暂无流水（本表建立之前的历史单据不补造流水）</template>
      </el-table>
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
.ledger-head { display: flex; align-items: baseline; gap: 12px; margin: 20px 0 8px; flex-wrap: wrap; }
.ledger { max-width: 860px; }
.qty-plus { color: #67c23a; font-weight: 600; }
.qty-minus { color: #f56c6c; font-weight: 600; }
</style>
