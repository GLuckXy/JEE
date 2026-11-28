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
const selected = ref([])
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
const typeLabels = { EASY: '基础', MEDIUM: '进阶', HARD: '挑战' }
const typeLabel = (value) => typeLabels[value] || value
const topicOptions = computed(() => [...new Set(questions.value.map((q) => q.topic))])
const typeOptions = computed(() => [...new Set(questions.value.map((q) => q.type))])

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
const sources = computed(() => [
  ...sourceAnswers.value.map((a) => ({ key: a.id, questionId: a.questionId, answerId: a.id,
    title: a.questionTitle + ' · 第 ' + a.roundNumber + ' 轮 · ' + new Date(a.createdAt).toLocaleString('zh-CN') })),
  ...specialties.value.recommendations.map((r) => ({ key: r.questionId, questionId: r.questionId,
    title: (questions.value.find((q) => q.id === r.questionId)?.title || r.topic) + ' · 推荐题目' })),
])

let revision = 0
let sourcesRevision = 0

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
    const [items, groups] = await Promise.all([api.questions(roleId.value),
      special.value ? api.specialties(roleId.value) : null])
    if (current !== revision) return
    questions.value = items
    if (groups) {
      specialties.value = groups
      filter.value = 'topic'
      filterValue.value = groups.topics[0] || ''
    }
  } catch (e) {
    if (current === revision) error.value = message(e)
  } finally {
    if (current === revision) {
      loading.value = false
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

function toggle(id, checked) {
  if (checked && selected.value.length >= 10) {
    error.value = '每次最多选择 10 道题。'
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
      <section :id="special ? undefined : 'interviewSetup'" class="panel surface" :class="{ 'training-setup': !special }">
        <div class="setup-main">
          <h2>训练配置</h2>
          <div class="field">
            <label for="training-role">面试岗位</label>
            <ElSelect id="training-role" v-model="roleId" :disabled="creating || !roles.length">
              <ElOption v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
            </ElSelect>
          </div>
          <template v-if="special">
            <div class="field">
              <label for="training-filter">专项类别</label>
              <ElSelect id="training-filter" v-model="filter" :disabled="loading || creating"
                @change="filterValue = (filter === 'topic' ? specialties.topics : specialties.types)[0] || ''">
                <ElOption label="按知识点" value="topic" />
                <ElOption label="按难度" value="type" />
              </ElSelect>
            </div>
            <div class="field">
              <label for="training-filter-value">{{ filter === 'topic' ? '知识点' : '难度' }}</label>
              <ElSelect id="training-filter-value" v-model="filterValue" :disabled="loading || creating">
                <ElOption v-for="value in (filter === 'topic' ? specialties.topics : specialties.types)"
                  :key="value" :label="value" :value="value" />
              </ElSelect>
            </div>
          </template>
        </div>
        <aside v-if="!special" class="setup-aside" aria-label="当前训练概况">
          <div class="setup-stat num">{{ filteredCandidates.length }}<span>道可选题目</span></div>
          <p class="fine-print">自选岗位和题目，用文字或语音作答，提交后查看逐题反馈与改进建议。</p>
          <p class="setup-hint"><span class="setup-hint__dot" aria-hidden="true" />每次最多选择 <b>10</b> 道题</p>
        </aside>
      </section>
      <section v-if="special" class="panel surface">
        <h2>薄弱点与推荐</h2>
        <p v-if="!loading && !specialties.recommendations.length">当前岗位暂无推荐。</p>
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

    <section class="panel surface">
      <div class="row-heading">
        <h2>{{ special ? '可练题组' : '岗位题目' }}</h2>
        <span v-if="selected.length" class="tag green num">已选 {{ selected.length }} / 10</span>
      </div>
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
        <ElButton v-if="selected.length" text type="danger" :disabled="creating" @click="clearSelected">清空已选</ElButton>
      </div>
      <p v-if="loading">正在加载题目…</p>
      <p v-else-if="!filteredCandidates.length">当前条件下没有可用题目。</p>
      <div v-for="q in visibleCandidates" :key="q.id" class="question-row">
        <ElCheckbox :model-value="selected.includes(q.id)" :disabled="creating" @change="toggle(q.id, $event)">
          {{ q.title }}
        </ElCheckbox>
        <div class="tags"><span class="tag">{{ q.topic }}</span><span class="tag green">{{ typeLabel(q.type) }}</span></div>
        <p class="question-prompt" :class="{ open: expandedPrompts.includes(q.id) }" :title="expandedPrompts.includes(q.id) ? '点击收起题干' : '点击展开题干'" @click="togglePrompt(q.id)">{{ q.prompt }}</p>
      </div>
      <p v-if="hiddenCount > 0" class="fine-print">
        还有 {{ hiddenCount }} 道题未显示。
        <ElButton text type="primary" size="small" @click="showAll = !showAll">{{ showAll ? '收起' : '显示全部 ' + filteredCandidates.length + ' 题' }}</ElButton>
      </p>
      <div class="actions">
        <ElButton type="success" :loading="creating"
          :disabled="loading || !filteredCandidates.length || (!special && !selected.length)" @click="start()">
          {{ selected.length ? '开始训练（' + selected.length + ' 题）' : '开始训练' }}
        </ElButton>
        <small v-if="special && candidates.length && !selected.length">默认练习当前题组，最多 10 题。</small>
      </div>
    </section>

    <section class="panel surface training-history" aria-labelledby="training-history-title">
      <div class="row-heading">
        <h2 id="training-history-title">继续训练</h2>
        <RouterLink class="inline-link" to="/reports">查看全部历史与报告 →</RouterLink>
      </div>
      <p v-if="!unfinished.length" class="muted">暂无进行中或已暂停的训练。</p>
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
