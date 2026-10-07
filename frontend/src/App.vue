<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import router from './router'
import api from './api'

import BookView from './views/BookView.vue'
import BookDetailView from './views/BookDetailView.vue'
import SupplierView from './views/SupplierView.vue'
import PurchaseView from './views/PurchaseView.vue'
import SaleView from './views/SaleView.vue'

// ------------------------------------------------------------------
// 导航形态：左侧竖排导航（el-menu vertical）。
// 4 个业务模块：图书 / 供应商 / 进货单 / 销售单。
// 图书详情（/books/:id）不在导航里，由图书列表 / 销售单明细跳入。
// ------------------------------------------------------------------

const modules = [
  { path: '/books',     label: '图书',   icon: '📚', comp: BookView },
  { path: '/suppliers', label: '供应商', icon: '🏭', comp: SupplierView },
  { path: '/purchases', label: '进货单', icon: '📥', comp: PurchaseView },
  { path: '/sales',     label: '销售单', icon: '🧾', comp: SaleView }
]

const currentPath = ref(router.currentRoute.value.path)
const counts = ref({})

const isBookDetail = computed(() => router.currentRoute.value.name === 'book-detail')

const currentView = computed(() => {
  if (isBookDetail.value) return BookDetailView
  const m = modules.find((mm) => mm.path === currentPath.value)
  return m ? m.comp : BookView
})

// 详情页高亮「图书」导航项
const activePath = computed(() => (isBookDetail.value ? '/books' : currentPath.value))

async function loadCounts() {
  try {
    const [b, s, p, sa] = await Promise.all([
      api.listBooks(), api.listSuppliers(), api.listPurchases(), api.listSales()
    ])
    counts.value = {
      '/books': b.length,
      '/suppliers': s.length,
      '/purchases': p.length,
      '/sales': sa.length
    }
  } catch (e) { /* 计数失败不影响主流程 */ }
}

function go(path) {
  router.push(path)
}

function titleOf(path) {
  if (isBookDetail.value) return '图书详情'
  const m = modules.find((mm) => mm.path === path)
  return m ? m.label : ''
}

watch(() => router.currentRoute.value.path, (p) => { currentPath.value = p })

onMounted(async () => {
  currentPath.value = router.currentRoute.value.path
  await loadCounts()
})
</script>

<template>
  <div class="app-shell">
    <!-- 左侧竖排导航 -->
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">书</span>
        <div class="brand-text">
          <div class="brand-title">书店</div>
          <div class="brand-sub">图书台账与进销存</div>
        </div>
      </div>

      <el-menu
        :default-active="activePath"
        class="nav"
        @select="go"
      >
        <el-menu-item v-for="m in modules" :key="m.path" :index="m.path">
          <span class="nav-icon">{{ m.icon }}</span>
          <span class="nav-label">{{ m.label }}</span>
          <span class="nav-count" v-if="counts[m.path] !== undefined">{{ counts[m.path] }}</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-foot">靛蓝主题 · 竖排导航</div>
    </aside>

    <!-- 主内容区 -->
    <main class="content">
      <header class="crumb">
        <span class="crumb-root">书店</span>
        <span class="crumb-sep">/</span>
        <span class="crumb-cur">{{ titleOf(currentPath) }}</span>
      </header>
      <section class="panel">
        <component :is="currentView" @changed="loadCounts" />
      </section>
    </main>
  </div>
</template>

<style scoped>
.app-shell { display: flex; min-height: 100vh; }

.sidebar {
  width: 220px; flex-shrink: 0;
  background: linear-gradient(180deg, var(--primary-deep) 0%, var(--primary) 100%);
  color: #fff; display: flex; flex-direction: column;
  box-shadow: 2px 0 12px rgba(63, 81, 181, 0.22);
}
.brand { display: flex; align-items: center; gap: 10px; padding: 18px 16px; }
.brand-mark {
  width: 36px; height: 36px; border-radius: 9px;
  background: #fff; color: var(--primary-deep);
  font-weight: 800; font-size: 19px; display: flex; align-items: center; justify-content: center;
}
.brand-title { font-size: 18px; font-weight: 700; }
.brand-sub { font-size: 11px; opacity: 0.82; margin-top: 2px; }

.nav { background: transparent; border-right: none; flex: 1; }
.nav :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.88);
  height: 52px; margin: 4px 10px; border-radius: 9px;
  display: flex; align-items: center; gap: 10px;
}
.nav :deep(.el-menu-item.is-active) {
  background: #fff; color: var(--primary-deep); font-weight: 600;
}
.nav :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.14); color: #fff;
}
.nav-icon { font-size: 17px; }
.nav-label { flex: 1; }
.nav-count {
  min-width: 20px; height: 20px; padding: 0 6px; border-radius: 10px;
  background: var(--primary-deep); color: #fff; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
}
.nav :deep(.el-menu-item.is-active) .nav-count { background: var(--primary); }

.sidebar-foot { padding: 14px 16px; font-size: 11px; opacity: 0.7; }

.content { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.crumb { padding: 16px 24px; font-size: 13px; color: var(--muted); border-bottom: 1px solid var(--line); }
.crumb-root { color: var(--muted); }
.crumb-sep { margin: 0 6px; }
.crumb-cur { color: var(--ink); font-weight: 600; font-size: 15px; }
.panel { flex: 1; padding: 22px 24px; overflow: auto; }
</style>
