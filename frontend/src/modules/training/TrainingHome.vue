<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElOption, ElSelect } from 'element-plus'
import { api, duration, message, sessionPath, stateLabel } from './api'

const props = defineProps({ mode: { type: String, required: true } })
const router = useRouter()
const special = computed(() => props.mode === 'SPECIAL')
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
const error = ref('')
const loading = ref(true)
const creating = ref(false)

const candidates = computed(() => special.value
  ? questions.value.filter((q) => filterValue.value && q[filter.value] === filterValue.value)
  : questions.value)
const sources = computed(() => [
  ...sourceAnswers.value.map((a) => ({ key: a.id, questionId: a.questionId, answerId: a.id,
    title: a.questionTitle + ' · 第 ' + a.roundNumber + ' 轮 · ' + new Date(a.createdAt).toLocaleString('zh-CN') })),
  ...specialties.value.recommendations.map((r) => ({ key: r.questionId, questionId: r.questionId,
    title: (questions.value.find((q) => q.id === r.questionId)?.title || r.topic) + ' · 推荐题目' })),
])

let revision = 0
async function loadRole() {
  const current = ++revision
  error.value = ''
  loading.value = true
  selected.value = []
  sourceKey.value = ''
  sourceAnswers.value = []
  questions.value = []
  specialties.value = { topics: [], types: [], recommendations: [] }
  try {
    const [items, groups] = await Promise.all([api.questions(roleId.value),
      special.value ? api.specialties(roleId.value) : null])
    if (current !== revision) return
    questions.value = items
    if (groups) {
      specialties.value = groups
      filter.value = 'topic'
      filterValue.value = groups.topics[0] || ''
      const records = await Promise.allSettled(history.value.filter((s) => s.roleId === roleId.value)
        .slice(0, 15).map((s) => api.progress(s.id)))
      if (current !== revision) return
      sourceAnswers.value = records.flatMap((r) => r.status === 'fulfilled' ? r.value.answers : [])
        .sort((a, b) => b.createdAt - a.createdAt)
      if (records.some((r) => r.status === 'rejected')) error.value = '部分历史回答加载失败，刷新页面可重试。'
      sourceKey.value = sources.value[0]?.key || ''
    }
  } catch (e) {
    if (current === revision) error.value = message(e)
  } finally {
    if (current === revision) loading.value = false
  }
}

onMounted(async () => {
  try {
    const [sessions, availableRoles] = await Promise.all([api.history(), api.roles()])
    history.value = sessions
    roles.value = availableRoles
    roleId.value = roles.value[0]?.id || ''
    if (!roleId.value) loading.value = false
  } catch (e) {
    error.value = message(e)
    loading.value = false
  }
})
watch(roleId, loadRole)
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
    <header class="page-heading training-heading"><div class="eyebrow">训练</div><h1>{{special ? '专项训练' : '普通训练'}}</h1><p class="muted">{{special ? '按知识点或难度选题，也可以针对以前的答题记录复练。' : '选择岗位和题目，按自己的节奏练习。'}}</p></header>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div class="grid two">
      <section :id="special ? undefined : 'interviewSetup'" class="panel surface">
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
      </section>
      <section v-if="!special" class="panel surface">
        <h2>本次规则</h2>
        <p>每题可用文字或语音作答。提交后查看参考要点和改进建议，再选择重答、下一题或结束。</p>
        <p class="notice">暂停会保存当前题与草稿。语音识别和评价等待分别计时。</p>
      </section>
      <section v-else class="panel surface">
        <h2>薄弱点与推荐</h2>
        <p v-if="!loading && !specialties.recommendations.length">当前岗位暂无推荐。</p>
        <div v-for="item in specialties.recommendations" :key="item.questionId" class="recommendation">
          <strong>{{ item.topic }}</strong>
          <p>{{ item.reason }}</p>
          <ElButton text type="primary" :disabled="loading || creating" @click="recommend(item)">查看推荐题组</ElButton>
        </div>
      </section>
    </div>

    <section v-if="special" class="panel surface">
      <h2>选择复练方式</h2>
      <div class="field">
        <label for="source-answer">来源回答或推荐题目</label>
        <ElSelect id="source-answer" v-model="sourceKey" :disabled="loading || creating || !sources.length"
          placeholder="当前岗位暂无复练来源">
          <ElOption v-for="source in sources" :key="source.key" :label="source.title" :value="source.key" />
        </ElSelect>
      </div>
      <div class="actions">
        <ElButton type="success" :disabled="loading || creating || !sourceKey" @click="start('ORIGINAL')">重答原题</ElButton>
        <ElButton :disabled="loading || creating || !sourceKey" @click="start('SIMILAR')">练同知识点新题</ElButton>
      </div>
    </section>

    <section class="panel surface">
      <h2>{{ special ? '可练题组' : '岗位题目' }}</h2>
      <p v-if="loading">正在加载题目…</p>
      <p v-else-if="!candidates.length">当前条件下没有可用题目。</p>
      <div v-for="q in candidates" :key="q.id" class="question-row">
        <ElCheckbox :model-value="selected.includes(q.id)" :disabled="creating" @change="toggle(q.id, $event)">
          {{ q.title }}
        </ElCheckbox>
        <div class="tags"><span class="tag">{{ q.topic }}</span><span class="tag green">{{ q.type }}</span></div>
        <p>{{ q.prompt }}</p>
      </div>
      <div class="actions">
        <ElButton type="success" :loading="creating"
          :disabled="loading || !candidates.length || (!special && !selected.length)" @click="start()">
          {{ selected.length ? '开始训练（' + selected.length + ' 题）' : '开始训练' }}
        </ElButton>
        <small v-if="special && candidates.length && !selected.length">默认练习当前题组，最多 10 题。</small>
      </div>
    </section>

    <section class="panel surface">
      <h2>历史记录</h2>
      <p v-if="!history.length">完成或暂停一场训练后，可从这里查看记录。</p>
      <ul v-else class="history-list">
        <li v-for="record in history" :key="record.id">
          <RouterLink :to="sessionPath(record.mode, record.id)">
            {{ record.mode === 'SPECIAL' ? '专项训练' : '普通训练' }} ·
            {{ roles.find((role) => role.id === record.roleId)?.name || record.roleId }}
          </RouterLink>
          <span class="num">{{ new Date(record.createdAt).toLocaleString('zh-CN') }} · {{ record.total }} 题 · {{ duration(record.effectiveMs) }}</span>
          <span class="tag" :class="{ green: record.state === 'ENDED', orange: record.state === 'ACTIVE' }">{{ stateLabel(record.state) }}</span>
        </li>
      </ul>
    </section>
  </main>
</template>
