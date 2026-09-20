<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { todoApi } from './api/todo'

/* ============ 状态 ============ */
const todos = ref([])
const stats = ref({ total: 0, finished: 0, pending: 0 })
const loading = ref(false)
const busyId = ref(null)          // 正在请求中的条目 id，用来禁用按钮
const filter = ref('all')         // all | pending | finished
const keyword = ref('')

const form = reactive({ title: '', description: '', deadline: '' })
const submitting = ref(false)

const editingId = ref(null)
const editForm = reactive({ title: '', description: '', deadline: '' })

const toast = reactive({ show: false, type: 'success', text: '' })

const filterOptions = [
  { label: '全部', value: 'all' },
  { label: '待完成', value: 'pending' },
  { label: '已完成', value: 'finished' }
]

/* ============ 工具函数 ============ */

// 后端要 "2026-09-21 18:00:00"，而 <input type="datetime-local"> 给的是 "2026-09-21T18:00"
function toApiDatetime(value) {
  if (!value) return null
  return value.replace('T', ' ') + (value.length === 16 ? ':00' : '')
}

// 反过来：把后端返回的时间塞回输入框
function toInputDatetime(value) {
  if (!value) return ''
  return value.replace(' ', 'T').slice(0, 16)
}

let toastTimer = null
function showToast(text, type = 'success') {
  toast.text = text
  toast.type = type
  toast.show = true
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.show = false
  }, 2400)
}

/* ============ 数据加载 ============ */
async function loadTodos() {
  loading.value = true
  try {
    const params = {}
    if (filter.value === 'pending') params.done = false
    if (filter.value === 'finished') params.done = true
    if (keyword.value.trim()) params.keyword = keyword.value.trim()

    todos.value = await todoApi.list(params)
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await todoApi.stats()
  } catch (e) {
    // 统计失败不影响主流程，静默处理
  }
}

async function refresh() {
  await Promise.all([loadTodos(), loadStats()])
}

/* ============ 新增 ============ */
function resetForm() {
  form.title = ''
  form.description = ''
  form.deadline = ''
}

async function handleCreate() {
  if (!form.title.trim()) {
    showToast('请先填写标题', 'error')
    return
  }
  submitting.value = true
  try {
    await todoApi.create({
      title: form.title.trim(),
      description: form.description.trim(),
      deadline: toApiDatetime(form.deadline)
    })
    resetForm()
    await refresh()
    showToast('添加成功')
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    submitting.value = false
  }
}

/* ============ 编辑 ============ */
function startEdit(todo) {
  editingId.value = todo.id
  editForm.title = todo.title
  editForm.description = todo.description || ''
  editForm.deadline = toInputDatetime(todo.deadline)
}

function cancelEdit() {
  editingId.value = null
}

async function saveEdit(id) {
  if (!editForm.title.trim()) {
    showToast('标题不能为空', 'error')
    return
  }
  try {
    await todoApi.update(id, {
      title: editForm.title.trim(),
      // 注意：这里传空字符串而不是 null
      // 因为 MyBatis-Plus 默认跳过 null 字段，传 null 就清不掉原来的描述
      description: editForm.description.trim(),
      deadline: toApiDatetime(editForm.deadline)
    })
    editingId.value = null
    await refresh()
    showToast('已保存')
  } catch (e) {
    showToast(e.message, 'error')
  }
}

/* ============ 切换完成 / 删除 ============ */
async function handleToggle(todo) {
  busyId.value = todo.id
  try {
    await todoApi.toggle(todo.id)
    await refresh()
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    busyId.value = null
  }
}

async function handleDelete(todo) {
  if (!window.confirm(`确定要删除「${todo.title}」吗？`)) return
  busyId.value = todo.id
  try {
    await todoApi.remove(todo.id)
    await refresh()
    showToast('已删除')
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    busyId.value = null
  }
}

/* ============ 计算属性 ============ */
const progress = computed(() => {
  if (!stats.value.total) return 0
  return Math.round((stats.value.finished / stats.value.total) * 100)
})

/* ============ 监听与初始化 ============ */
watch(filter, loadTodos)

let searchTimer = null
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadTodos, 300)
})

onMounted(refresh)
</script>

<template>
  <div class="page">
    <header class="hero">
      <h1>待办清单</h1>
      <p>Spring Boot 3 + MySQL + MyBatis-Plus + Vue 3 全栈示例</p>
    </header>

    <main class="container">
      <!-- 数据统计 -->
      <section class="stats">
        <div class="stat">
          <span class="stat-num">{{ stats.total }}</span>
          <span class="stat-label">全部</span>
        </div>
        <div class="stat">
          <span class="stat-num c-pending">{{ stats.pending }}</span>
          <span class="stat-label">待完成</span>
        </div>
        <div class="stat">
          <span class="stat-num c-finished">{{ stats.finished }}</span>
          <span class="stat-label">已完成</span>
        </div>
        <div class="stat stat-progress">
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: progress + '%' }"></div>
          </div>
          <span class="stat-label">完成进度 {{ progress }}%</span>
        </div>
      </section>

      <!-- 新增 -->
      <section class="card">
        <h2 class="card-title">新增待办</h2>
        <form class="composer" @submit.prevent="handleCreate">
          <input
            v-model="form.title"
            class="input"
            placeholder="要做什么？"
            maxlength="200"
          />
          <input
            v-model="form.description"
            class="input"
            placeholder="补充说明（选填）"
            maxlength="500"
          />
          <div class="composer-foot">
            <label class="field">
              <span class="field-label">截止时间</span>
              <input v-model="form.deadline" type="datetime-local" class="input" />
            </label>
            <button type="submit" class="btn btn-primary" :disabled="submitting">
              {{ submitting ? '添加中…' : '添加' }}
            </button>
          </div>
        </form>
      </section>

      <!-- 列表 -->
      <section class="card">
        <div class="toolbar">
          <div class="filters">
            <button
              v-for="item in filterOptions"
              :key="item.value"
              class="chip"
              :class="{ active: filter === item.value }"
              @click="filter = item.value"
            >
              {{ item.label }}
            </button>
          </div>
          <input v-model="keyword" class="input search" placeholder="搜索标题…" />
        </div>

        <div v-if="loading" class="hint">加载中…</div>
        <div v-else-if="todos.length === 0" class="hint">
          这里空空的，先添加一条吧
        </div>

        <ul v-else class="list">
          <li
            v-for="todo in todos"
            :key="todo.id"
            class="item"
            :class="{ 'is-done': todo.done, 'is-busy': busyId === todo.id }"
          >
            <!-- 编辑态 -->
            <div v-if="editingId === todo.id" class="edit-box">
              <input v-model="editForm.title" class="input" placeholder="标题" maxlength="200" />
              <input
                v-model="editForm.description"
                class="input"
                placeholder="补充说明"
                maxlength="500"
              />
              <div class="edit-foot">
                <input v-model="editForm.deadline" type="datetime-local" class="input" />
                <button class="btn btn-primary btn-sm" @click="saveEdit(todo.id)">保存</button>
                <button class="btn btn-ghost btn-sm" @click="cancelEdit">取消</button>
              </div>
            </div>

            <!-- 展示态 -->
            <template v-else>
              <button
                class="check"
                :class="{ checked: todo.done }"
                :title="todo.done ? '标记为未完成' : '标记为已完成'"
                @click="handleToggle(todo)"
              >
                <svg v-if="todo.done" viewBox="0 0 16 16" width="12" height="12">
                  <path
                    d="M3 8.5l3.2 3.2L13 5"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2.4"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>

              <div class="item-main">
                <p class="item-title">{{ todo.title }}</p>
                <p v-if="todo.description" class="item-desc">{{ todo.description }}</p>
                <div class="item-meta">
                  <span v-if="todo.deadline" class="tag tag-deadline">
                    截止 {{ todo.deadline }}
                  </span>
                  <span class="tag">创建于 {{ todo.createTime }}</span>
                </div>
              </div>

              <div class="item-actions">
                <button class="btn btn-ghost btn-sm" @click="startEdit(todo)">编辑</button>
                <button class="btn btn-danger btn-sm" @click="handleDelete(todo)">删除</button>
              </div>
            </template>
          </li>
        </ul>
      </section>

      <footer class="footer">
        后端 http://localhost:8080 &nbsp;·&nbsp; 前端 http://localhost:5173
      </footer>
    </main>

    <transition name="toast">
      <div v-if="toast.show" class="toast" :class="'toast-' + toast.type">
        {{ toast.text }}
      </div>
    </transition>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding-bottom: 60px;
}

/* ---------- 顶部 ---------- */
.hero {
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  padding: 32px 24px 28px;
  text-align: center;
}

.hero h1 {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.5px;
}

.hero p {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-3);
}

.container {
  max-width: 780px;
  margin: 0 auto;
  padding: 28px 20px 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ---------- 统计 ---------- */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  box-shadow: var(--shadow-sm);
}

.stat-num {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
}

.c-pending {
  color: var(--warning);
}

.c-finished {
  color: var(--success);
}

.stat-label {
  font-size: 12px;
  color: var(--text-3);
}

.stat-progress {
  justify-content: center;
  gap: 8px;
}

.progress-track {
  width: 100%;
  height: 7px;
  background: var(--border);
  border-radius: 999px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--success);
  border-radius: 999px;
  transition: width 0.35s ease;
}

/* ---------- 卡片 ---------- */
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 22px;
  box-shadow: var(--shadow);
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 14px;
}

/* ---------- 输入 ---------- */
.input {
  width: 100%;
  padding: 10px 13px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: #fbfcfe;
  font-size: 14px;
  transition: border-color 0.15s, background 0.15s;
}

.input:focus {
  border-color: var(--primary);
  background: var(--surface);
}

.input::placeholder {
  color: var(--text-3);
}

.composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.composer-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  flex: 1;
}

.field-label {
  font-size: 12px;
  color: var(--text-3);
}

/* ---------- 按钮 ---------- */
.btn {
  padding: 10px 20px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s, color 0.15s, opacity 0.15s;
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--primary);
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: var(--primary-hover);
}

.btn-ghost {
  color: var(--text-2);
  border: 1px solid var(--border);
  background: var(--surface);
}

.btn-ghost:hover {
  border-color: var(--text-3);
  color: var(--text-1);
}

.btn-danger {
  color: var(--danger);
  border: 1px solid transparent;
  background: var(--danger-light);
}

.btn-danger:hover {
  background: #fee2e2;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 13px;
  border-radius: 6px;
}

/* ---------- 工具栏 ---------- */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  gap: 6px;
}

.chip {
  padding: 6px 14px;
  font-size: 13px;
  border-radius: 999px;
  color: var(--text-2);
  background: var(--bg);
  transition: background 0.15s, color 0.15s;
}

.chip:hover {
  background: #e2e8f0;
}

.chip.active {
  background: var(--primary);
  color: #fff;
}

.search {
  width: 200px;
}

/* ---------- 列表 ---------- */
.list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  transition: border-color 0.15s, background 0.15s;
}

.item:hover {
  border-color: #cbd5e1;
}

.item.is-busy {
  opacity: 0.5;
  pointer-events: none;
}

.item.is-done {
  background: #fafbfc;
}

.item.is-done .item-title {
  text-decoration: line-through;
  color: var(--text-3);
}

.check {
  flex-shrink: 0;
  width: 21px;
  height: 21px;
  margin-top: 2px;
  border: 1.5px solid #cbd5e1;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: transparent;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.check:hover {
  border-color: var(--success);
}

.check.checked {
  background: var(--success);
  border-color: var(--success);
  color: #fff;
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: 15px;
  font-weight: 500;
  word-break: break-word;
}

.item-desc {
  font-size: 13px;
  color: var(--text-2);
  margin-top: 3px;
  word-break: break-word;
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.tag {
  font-size: 11px;
  color: var(--text-3);
  background: var(--bg);
  padding: 2px 8px;
  border-radius: 999px;
}

.tag-deadline {
  color: var(--warning);
  background: #fffbeb;
}

.item-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

/* ---------- 编辑态 ---------- */
.edit-box {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.edit-foot {
  display: flex;
  gap: 8px;
  align-items: center;
}

.edit-foot .input {
  flex: 1;
}

/* ---------- 其他 ---------- */
.hint {
  padding: 36px 0;
  text-align: center;
  font-size: 14px;
  color: var(--text-3);
}

.footer {
  text-align: center;
  font-size: 12px;
  color: var(--text-3);
  padding-top: 4px;
}

.toast {
  position: fixed;
  left: 50%;
  bottom: 40px;
  transform: translateX(-50%);
  padding: 11px 22px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: #fff;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.16);
  z-index: 100;
}

.toast-success {
  background: var(--success);
}

.toast-error {
  background: var(--danger);
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.22s, transform 0.22s;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(10px);
}

@media (max-width: 640px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .search {
    width: 100%;
  }

  .item {
    flex-wrap: wrap;
  }

  .item-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
