<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElInput, ElOption, ElSelect } from 'element-plus'
import SegmentedControl from '../../components/SegmentedControl.vue'
import { api, duration, message, sessionPath, stateLabel } from './api'

const props = defineProps({ mode: { type: String, required: true } })
const router = useRouter()
const special = computed(() => props.mode === 'SPECIAL')
const modeOptions = [
  { value: '/training', label: '普通自选' },
  { value: '/practice', label: '专项突破' },
]
const roles = ref([])
const roleId = ref('')
const questions = ref([])
const specialties = ref({ topics: [], types: [], recommendations: [] })
const history = ref([])
const filter = ref('topic')
const filterValue = ref('')
const selected = ref([])            // 有序数组，即作答顺序
const practiceCount = ref({})       // questionId -> 历史作答次数（真实数据）
const practiceLoaded = ref(false)
const orderOpen = ref(true)
const sourceKey = ref('')
const sourceAnswers = ref([])
const sourceOpen = ref(false)
const sourceLoading = ref(false)
const sourceError = ref('')
const error = ref('')
const loading = ref(true)
const creating = ref(false)
const search = ref('')
const topicFilter = ref('')
const typeFilter = ref('')
const showAll = ref(false)
const expandedPrompts = ref([])

const previewCount = 6
const maxSelected = 10
const typeLabels = { EASY: '基础', MEDIUM: '进阶', HARD: '挑战' }
const minutesByType = { EASY: 3, MEDIUM: 5, HARD: 8 }
const typeLabel = (value) => typeLabels[value] || value
const topicOptions = computed(() => [...new Set(questions.value.map((q) => q.topic))])
const typeOptions = computed(() => [...new Set(questions.value.map((q) => q.type))])

// 预计作答时间：按难度基准 + 长题干加 1 分钟（前端估算，无需后端字段）
function estimate(q) {
  const base = minutesByType[q.type] || 5
  return base + ((q.prompt || '').length > 120 ? 1 : 0)
}
const countOf = (id) => practiceCount.value[String(id)] || 0
const practicedCount = computed(() => questions.value.filter((q) => countOf(q.id) > 0).length)
const estimatedTotal = computed(() => selected.value.reduce((sum, id) => {
  const q = questions.value.find((item) => item.id === id)
  return sum + (q ? estimate(q) : 0)
}, 0))
const atLimit = computed(() => selected.value.length >= maxSelected)
const isDisabled = (q) => creating.value || (atLimit.value && !selected.value.includes(q.id))

const unfinished = computed(() => history.value.filter(({ state }) => state === 'ACTIVE' || state === 'PAUSED').slice(0, 3))
const candidates = computed(() => special.value
  ? questions.value.filter((q) => filterValue.value && q[filter.value] === filterValue.value)
  : questions.value)
const filteredCandidates = computed(() => {
  let list = candidates.value
  if (!special.value) {
    if (topicFilter.value) list = list.filter((q) => q.topic === topicFilter.value)
    if (typeFilter.value) list = list.filter((q) => q.type === typeFilter.value)
  }
  const keyword = search.value.trim().toLowerCase()
  if (keyword) list = list.filter((q) => q.title.toLowerCase().includes(keyword) || q.prompt.toLowerCase().includes(keyword))
  return list
})
const visibleCandidates = computed(() => showAll.value ? filteredCandidates.value : filteredCandidates.value.slice(0, previewCount))
const hiddenCount = computed(() => filteredCandidates.value.length - visibleCandidates.value.length)

// 按考察点分组：让「按考察点组卷」成为一等公民，而不是手工多选
const groupedCandidates = computed(() => {
  const groups = new Map()
  for (const q of visibleCandidates.value) {
    if (!groups.has(q.topic)) groups.set(q.topic, [])
    groups.get(q.topic).push(q)
  }
  return [...groups].map(([topic, items]) => ({
    topic,
    items,
    practiced: items.filter((q) => countOf(q.id) > 0).length,
  }))
})

// 推荐训练：优先用后端真实薄弱点（feedback.missing），无历史时降级为「练习覆盖最少」的知识点
const recommendations = computed(() => {
  const backend = (specialties.value.recommendations || []).slice(0, 2).map((r) => ({
    topic: r.topic, reason: r.reason || '近期作答有未覆盖的要点', source: 'weak',
  }))
  if (backend.length) return backend
  const byTopic = new Map()
  for (const q of questions.value) {
    const item = byTopic.get(q.topic) || { topic: q.topic, count: 0 }
    item.count += countOf(q.id)
    byTopic.set(q.topic, item)
  }
  return [...byTopic.values()].sort((a, b) => a.count - b.count).slice(0, 2).map((t) => ({
    topic: t.topic,
    reason: t.count === 0 ? '该考察点尚未练习' : `近期练习 ${t.count} 次，建议巩固`,
    source: 'coverage',
  }))
})

const sources = computed(() => [
  ...sourceAnswers.value.map((a) => ({ key: a.id, questionId: a.questionId, answerId: a.id,
    title: a.questionTitle + ' · 第 ' + a.roundNumber + ' 轮 · ' + new Date(a.createdAt).toLocaleString('zh-CN') })),
  ...specialties.value.recommendations.map((r) => ({ key: r.questionId, questionId: r.questionId,
    title: (questions.value.find((q) => q.id === r.questionId)?.title || r.topic) + ' · 推荐题目' })),
])

let revision = 0
let sourcesRevision = 0

// 聚合真实练习次数：遍历历史会话的 answers（AnswerView 带 questionId）
async function loadPracticeStats() {
  const current = revision
  const sessions = history.value.filter((s) => s.roleId === roleId.value).slice(0, 30)
  const records = await Promise.allSettled(sessions.map((s) => api.progress(s.id)))
  if (current !== revision) return
  const counts = {}
  for (const record of records) {
    if (record.status !== 'fulfilled') continue
    for (const answer of (record.value.answers || [])) {
      const key = String(answer.questionId)
      counts[key] = (counts[key] || 0) + 1
    }
  }
  practiceCount.value = counts
  practiceLoaded.value = true
}

async function loadSources() {
  const current = revision
  if (!special.value || !sourceOpen.value || loading.value || current === sourcesRevision) return
  sourcesRevision = current
  sourceLoading.value = true
  const records = await Promise.allSettled(history.value.filter((s) => s.roleId === roleId.value)
    .slice(0, 15).map((s) => api.progress(s.id)))
  if (current !== revision) return
  sourceAnswers.value = records.flatMap((r) => r.status === 'fulfilled' ? r.value.answers : [])
    .sort((a, b) => b.createdAt - a.createdAt)
  if (records.some((r) => r.status === 'rejected')) sourceError.value = '部分历史回答加载失败，刷新页面可重试。'
  if (!sourceKey.value) sourceKey.value = sources.value[0]?.key || ''
  sourceLoading.value = false
}

async function loadRole() {
  const current = ++revision
  error.value = ''
  loading.value = true
  selected.value = []
  practiceCount.value = {}
  practiceLoaded.value = false
  orderOpen.value = true
  sourceKey.value = ''
  sourceAnswers.value = []
  sourceLoading.value = false
  sourceError.value = ''
  questions.value = []
  specialties.value = { topics: [], types: [], recommendations: [] }
  search.value = ''
  topicFilter.value = ''
  typeFilter.value = ''
  showAll.value = false
  expandedPrompts.value = []
  try {
    // 两种模式都取 specialties：普通模式也需要后端真实薄弱点用于「推荐训练」
    const [items, groups] = await Promise.all([api.questions(roleId.value), api.specialties(roleId.value)])
    if (current !== revision) return
    questions.value = items
    if (groups) {
      specialties.value = groups
      if (special.value) {
        filter.value = 'topic'
        filterValue.value = groups.topics[0] || ''
      }
    }
  } catch (e) {
    if (current === revision) error.value = message(e)
  } finally {
    if (current === revision) {
      loading.value = false
      void loadPracticeStats()
      if (!error.value) void loadSources()
    }
  }
}

onMounted(async () => {
  const current = revision
  try {
    const [sessions, availableRoles] = await Promise.all([api.history(), api.roles()])
    if (current !== revision) return
    history.value = sessions
    roles.value = availableRoles
    roleId.value = roles.value[0]?.id || ''
    if (!roleId.value) loading.value = false
  } catch (e) {
    if (current !== revision) return
    error.value = message(e)
    loading.value = false
  }
})
onBeforeUnmount(() => { revision++ })
watch(roleId, loadRole)
watch(sourceOpen, loadSources)
watch([filter, filterValue], () => { selected.value = [] })

function recommend(item) {
  filter.value = 'topic'
  filterValue.value = item.topic
  sourceKey.value = item.questionId
}

// 按考察点一键组卷：清空后按题库顺序选中该考察点下最多 10 题
function practiceGroup(topic) {
  const ids = filteredCandidates.value.filter((q) => q.topic === topic).slice(0, maxSelected).map((q) => q.id)
  if (!ids.length) { error.value = '该考察点暂无可用题目。'; return }
  error.value = ''
  selected.value = ids
  orderOpen.value = true
}

function toggle(id, checked) {
  if (checked && atLimit.value) {
    error.value = `每次最多选择 ${maxSelected} 道题，请先移除已选题目。`
    return
  }
  selected.value = checked ? [...selected.value, id] : selected.value.filter((value) => value !== id)
}

function togglePrompt(id) {
  const index = expandedPrompts.value.indexOf(id)
  if (index === -1) expandedPrompts.value.push(id)
  else expandedPrompts.value.splice(index, 1)
}

function clearSelected() {
  selected.value = []
  error.value = ''
}

// 调整作答顺序：selected 的顺序即提交给后端的 questionIds 顺序（后端 LinkedHashSet 保序）
function moveSelected(index, delta) {
  const target = index + delta
  if (target < 0 || target >= selected.value.length) return
  const next = [...selected.value]
  ;[next[index], next[target]] = [next[target], next[index]]
  selected.value = next
}
function removeSelected(id) {
  selected.value = selected.value.filter((value) => value !== id)
  error.value = ''
}

async function start(strategy) {
  if (creating.value) return
  creating.value = true
  error.value = ''
  try {
    const source = sources.value.find((item) => item.key === sourceKey.value)
    const input = { mode: props.mode, roleId: roleId.value, questionIds: selected.value }
    if (strategy) {
      input.source = { strategy, questionId: source?.questionId, answerId: source?.answerId }
    } else if (special.value) {
      input[filter.value] = filterValue.value
    }
    const result = await api.create(input)
    await router.push(sessionPath(result.mode, result.id))
  } catch (e) {
    error.value = message(e)
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <main :id="special ? 'practice' : 'interview'" class="page-shell training-app page">
    <header class="page-heading training-heading heading-with-actions">
      <div>
        <div class="eyebrow">训练</div>
        <h1>{{ special ? '专项训练' : '普通训练' }}</h1>
        <p class="muted">{{ special ? '按知识点或难度选题，也可以针对以前的答题记录复练。' : '自选岗位和题目，用文字或语音作答，提交后查看反馈。' }}</p>
      </div>
      <SegmentedControl :model-value="special ? '/practice' : '/training'" :options="modeOptions" label="训练方式"
        @update:model-value="router.push($event)" />
    </header>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />

    <div :class="{ grid: special, two: special }">
      <!-- 配置区：压缩为单行 + 指标，不再占一整块高度 -->
      <section :id="special ? undefined : 'interviewSetup'" class="panel surface train-setup">
        <div class="setup-line">
          <div class="setup-field">
            <label for="training-role">面试岗位</label>
            <ElSelect id="training-role" v-model="roleId" :disabled="creating || !roles.length">
              <ElOption v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
            </ElSelect>
          </div>
          <template v-if="special">
            <div class="setup-field">
              <label for="training-filter">专项类别</label>
              <ElSelect id="training-filter" v-model="filter" :disabled="loading || creating"
                @change="filterValue = (filter === 'topic' ? specialties.topics : specialties.types)[0] || ''">
                <ElOption label="按知识点" value="topic" />
                <ElOption label="按难度" value="type" />
              </ElSelect>
            </div>
            <div class="setup-field">
              <label for="training-filter-value">{{ filter === 'topic' ? '知识点' : '难度' }}</label>
              <ElSelect id="training-filter-value" v-model="filterValue" :disabled="loading || creating">
                <ElOption v-for="value in (filter === 'topic' ? specialties.topics : specialties.types)"
                  :key="value" :label="value" :value="value" />
              </ElSelect>
            </div>
          </template>
          <div class="setup-metrics" aria-label="当前训练概况">
            <span><b class="num">{{ filteredCandidates.length }}</b> 道可选</span>
            <span><b class="num">{{ practicedCount }}</b> 道已练</span>
            <span>单次上限 <b class="num">10</b> 题</span>
          </div>
        </div>
        <!-- 推荐训练：普通模式基于薄弱点 / 练习覆盖度 -->
        <div v-if="!special && !loading && recommendations.length" class="recommend-strip">
          <span class="recommend-strip__label">推荐训练</span>
          <div v-for="item in recommendations" :key="item.topic" class="recommend-chip">
            <span class="recommend-chip__text"><strong>{{ item.topic }}</strong><small>{{ item.reason }}</small></span>
            <ElButton size="small" :disabled="creating" @click="practiceGroup(item.topic)">练这组</ElButton>
          </div>
        </div>
      </section>
      <section v-if="special" class="panel surface">
        <h2>薄弱点与推荐</h2>
        <p v-if="!loading && !specialties.recommendations.length" class="muted">当前岗位暂无历史薄弱点，可先从下方题组开始。</p>
        <div v-for="item in specialties.recommendations" :key="item.questionId" class="recommendation">
          <strong>{{ item.topic }}</strong>
          <p>{{ item.reason }}</p>
          <ElButton text type="primary" :disabled="loading || creating" @click="recommend(item)">查看推荐题组</ElButton>
        </div>
      </section>
    </div>

    <details v-if="special" class="panel surface training-repractice" @toggle="sourceOpen = $event.currentTarget.open">
      <summary>按错题复练</summary>
      <p v-if="sourceLoading" role="status">正在加载历史回答…</p>
      <ElAlert v-if="sourceError" :title="sourceError" type="warning" :closable="false" show-icon />
      <div class="field">
        <label for="source-answer">来源回答或推荐题目</label>
        <ElSelect id="source-answer" v-model="sourceKey" :disabled="loading || sourceLoading || creating || !sources.length"
          placeholder="当前岗位暂无复练来源">
          <ElOption v-for="source in sources" :key="source.key" :label="source.title" :value="source.key" />
        </ElSelect>
      </div>
      <div class="actions">
        <ElButton type="primary" :disabled="loading || sourceLoading || creating || !sourceKey" @click="start('ORIGINAL')">重答原题</ElButton>
        <ElButton :disabled="loading || sourceLoading || creating || !sourceKey" @click="start('SIMILAR')">练同知识点新题</ElButton>
      </div>
    </details>

    <section class="panel surface question-panel">
      <div class="row-heading">
        <h2>{{ special ? '可练题组' : '岗位题目' }}</h2>
        <span class="tag green num">已选 {{ selected.length }} / 10</span>
      </div>
      <!-- 合并筛选控件：知识点 / 难度 / 搜索 / 清空 同一条工具栏 -->
      <div v-if="candidates.length" class="question-toolbar">
        <template v-if="!special">
          <ElSelect v-model="topicFilter" class="toolbar-select" placeholder="全部知识点" clearable :disabled="loading || creating">
            <ElOption v-for="value in topicOptions" :key="value" :label="value" :value="value" />
          </ElSelect>
          <ElSelect v-model="typeFilter" class="toolbar-select toolbar-select--short" placeholder="全部难度" clearable :disabled="loading || creating">
            <ElOption v-for="value in typeOptions" :key="value" :label="typeLabel(value)" :value="value" />
          </ElSelect>
        </template>
        <ElInput v-model="search" class="toolbar-search" placeholder="搜索题目关键词" clearable :disabled="loading || creating" />
        <span class="toolbar-spacer" />
        <ElButton v-if="selected.length" text type="danger" :disabled="creating" @click="clearSelected">清空已选</ElButton>
      </div>
      <p v-if="loading">正在加载题目…</p>
      <p v-else-if="!filteredCandidates.length">当前条件下没有可用题目。</p>
      <template v-else>
        <div v-for="group in groupedCandidates" :key="group.topic" class="q-group">
          <div class="q-group__head">
            <span class="q-group__title">{{ group.topic }}</span>
            <span class="q-group__meta">{{ group.items.length }} 题 · 已练 {{ group.practiced }} 题</span>
            <ElButton text type="primary" size="small" :disabled="creating" @click="practiceGroup(group.topic)">按此考察点组卷 →</ElButton>
          </div>
          <article v-for="q in group.items" :key="q.id" class="question-card"
            :class="{ 'is-selected': selected.includes(q.id), 'is-disabled': isDisabled(q), 'is-practiced': countOf(q.id) > 0 }">
            <div class="qc-head">
              <ElCheckbox :model-value="selected.includes(q.id)" :disabled="isDisabled(q)" @change="toggle(q.id, $event)">
                <span class="qc-title">{{ q.title }}</span>
              </ElCheckbox>
              <div class="qc-badges">
                <span class="tag green">{{ typeLabel(q.type) }}</span>
                <span v-if="countOf(q.id)" class="qc-practiced">已练 {{ countOf(q.id) }} 次</span>
                <span v-else class="qc-new">未练习</span>
                <span class="qc-time">预计 {{ estimate(q) }} 分钟</span>
              </div>
            </div>
            <p class="question-prompt" :class="{ open: expandedPrompts.includes(q.id) }"
              :title="expandedPrompts.includes(q.id) ? '点击收起题干' : '点击展开题干'" @click="togglePrompt(q.id)">{{ q.prompt }}</p>
          </article>
        </div>
        <p v-if="hiddenCount > 0" class="fine-print">
          还有 {{ hiddenCount }} 道题未显示。
          <ElButton text type="primary" size="small" @click="showAll = !showAll">{{ showAll ? '收起' : '显示全部 ' + filteredCandidates.length + ' 题' }}</ElButton>
        </p>
      </template>
    </section>

    <!-- 固定操作区：已选题目 + 作答顺序 + 开始训练，随页面滚动常驻 -->
    <div v-if="selected.length || special" class="train-actionbar" :class="{ 'is-sticky': selected.length > 0 }" role="region" aria-label="已选题目与开始训练">
      <div class="ab-main">
        <div class="ab-summary">
          <strong class="num">{{ selected.length }}</strong> / 10 题
          <span v-if="selected.length">· 预计 {{ estimatedTotal }} 分钟</span>
          <span v-else-if="special" class="ab-hint">默认练习当前题组，最多 10 题</span>
          <span v-else class="ab-hint">勾选题目后即可开始</span>
        </div>
        <div class="ab-actions">
          <ElButton v-if="selected.length" text :disabled="creating" @click="orderOpen = !orderOpen">
            {{ orderOpen ? '收起顺序' : '调整顺序' }}
          </ElButton>
          <ElButton v-if="selected.length" text type="danger" :disabled="creating" @click="clearSelected">清空</ElButton>
          <ElButton type="success" :loading="creating"
            :disabled="loading || !filteredCandidates.length || (!special && !selected.length)" @click="start()">
            {{ selected.length ? '开始训练（' + selected.length + ' 题）' : '开始训练' }}
          </ElButton>
        </div>
      </div>
      <div v-if="orderOpen && selected.length" class="ab-order">
        <span class="ab-order__hint">作答顺序</span>
        <div class="ab-order__list">
          <div v-for="(id, index) in selected" :key="id" class="ab-order__item">
            <span class="num ab-order__index">{{ index + 1 }}</span>
            <span class="ab-order__title">{{ questions.find((q) => q.id === id)?.title || id }}</span>
            <button type="button" :disabled="index === 0 || creating" aria-label="上移" @click="moveSelected(index, -1)">↑</button>
            <button type="button" :disabled="index === selected.length - 1 || creating" aria-label="下移" @click="moveSelected(index, 1)">↓</button>
            <button type="button" :disabled="creating" aria-label="移除" @click="removeSelected(id)">×</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 继续训练：空状态压缩为一行引导 -->
    <section class="panel surface training-history" aria-labelledby="training-history-title">
      <div class="row-heading">
        <h2 id="training-history-title">继续训练</h2>
        <RouterLink class="inline-link" to="/reports">查看全部历史与报告 →</RouterLink>
      </div>
      <p v-if="!unfinished.length" class="history-empty">
        <span class="history-empty__mark" aria-hidden="true" />暂无进行中的训练，选好题目后即可开始。
      </p>
      <ul v-else class="history-list">
        <li v-for="record in unfinished" :key="record.id">
          <RouterLink :to="sessionPath(record.mode, record.id)">
            {{ record.mode === 'SPECIAL' ? '专项训练' : '普通训练' }} ·
            {{ roles.find((role) => role.id === record.roleId)?.name || record.roleId }}
          </RouterLink>
          <span class="num">{{ new Date(record.createdAt).toLocaleString('zh-CN') }} · {{ record.total }} 题 · {{ duration(record.effectiveMs) }}</span>
          <span class="tag" :class="{ orange: record.state === 'ACTIVE' }">{{ stateLabel(record.state) }}</span>
        </li>
      </ul>
    </section>
  </main>
</template>
