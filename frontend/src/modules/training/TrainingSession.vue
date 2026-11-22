<script setup>
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElInput, ElProgress } from 'element-plus'
import VoiceRecorder from '../voice/VoiceRecorder.vue'
import AnswerFeedback from './AnswerFeedback.vue'
import { api, audioPath, duration, message, sessionPath, stateLabel } from './api'

const props = defineProps({ mode: { type: String, required: true } })
const route = useRoute()
const router = useRouter()
const id = String(route.params.id)
const session = ref(null)
const draft = ref('')
const audioId = ref(null)
const pendingAudio = shallowRef(null)
const voice = ref()
const recordPhase = ref('idle')
const services = ref(null)
const error = ref('')
const draftError = ref('')
const status = ref('')
const busy = ref('')
const evaluating = ref({})
const readUrl = ref('')
const clock = ref(0)
const special = computed(() => props.mode === 'SPECIAL')
const active = computed(() => session.value?.state === 'ACTIVE')
const answering = computed(() => session.value?.phase === 'ANSWERING')
const recording = computed(() => recordPhase.value !== 'idle')
const last = computed(() => session.value?.position + 1 === session.value?.total)
const records = computed(() => [...(session.value?.answers || [])].reverse())
const comparison = computed(() => session.value?.sourceAnswer &&
  (records.value.find((a) => a.position === session.value.position) || records.value[0]))
const tally = computed(() => new Set(records.value.map((a) => a.position)).size)
let alive = true
let sampledAt = 0
let ticker
let heartbeat
let saveTimer
let saving
let request
let ttsPlayer

function cursor() {
  return { position: session.value.position, roundNumber: session.value.roundNumber }
}

function draftInput() {
  return { ...cursor(), text: draft.value, audioId: audioId.value }
}

function accept(result, hydrate = true) {
  if (session.value) {
    const known = new Map(session.value.answers.map((item) => [item.id, item]))
    result = { ...result, answers: result.answers.map((item) => {
      const previous = known.get(item.id)
      return previous?.evaluationState === 'READY' ? previous : item
    }) }
  }
  session.value = result
  sampledAt = performance.now()
  clock.value = result.effectiveMs
  if (hydrate) {
    draft.value = result.draft
    audioId.value = result.draftAudioId
  }
}

function resetSpeech() {
  ttsPlayer?.pause()
  ttsPlayer = null
  if (readUrl.value) URL.revokeObjectURL(readUrl.value)
  readUrl.value = ''
}

async function save() {
  if (!alive || !session.value || !active.value || !answering.value || busy.value || document.hidden || saving) return
  const input = draftInput()
  saving = api.draft(id, input)
  try {
    const result = await saving
    if (alive) {
      accept(result, false)
      draftError.value = ''
    }
  } catch (e) {
    if (alive) draftError.value = '草稿暂未保存。' + message(e)
  } finally {
    saving = null
  }
}

function scheduleSave() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(save, 350)
}

function clearAudio() {
  audioId.value = null
  pendingAudio.value = null
  scheduleSave()
}

function onRecorded({ file }) {
  pendingAudio.value = file
}

async function upload() {
  if (!pendingAudio.value) return
  const asset = await api.upload(pendingAudio.value)
  audioId.value = asset.id
  pendingAudio.value = null
}

async function operate(label, callback) {
  if (busy.value || !session.value) return
  busy.value = label
  error.value = ''
  status.value = ''
  clearTimeout(saveTimer)
  try {
    if (saving) await saving.catch(() => {})
    await callback()
  } catch (e) {
    if (alive) error.value = message(e)
  } finally {
    if (alive) busy.value = ''
    if (label === '识别中' || label === '播报中') void save()
  }
}

function pause() {
  return operate('正在保存', async () => {
    if (recording.value) await voice.value?.stop()
    let failed = false
    try {
      await upload()
    } catch (e) {
      failed = true
      error.value = '录音上传失败。' + message(e)
    }
    accept(await api.action(id, 'pause', draftInput()))
    status.value = failed ? '文字和进度已保存。当前页面仍保留本段录音，可继续训练后重试上传。' : '已保存进度和草稿，可以刷新后继续。'
  })
}

function transition(action) {
  return operate('正在处理', async () => {
    const result = await api.action(id, action, cursor())
    if (action !== 'resume') {
      pendingAudio.value = null
      resetSpeech()
    }
    request = null
    accept(result)
    if (action === 'resume') status.value = '已继续训练。'
    if (action === 'end') status.value = '训练已结束，已提交的回答保存在历史记录中。'
  })
}

function saveAudio() {
  return operate('正在上传', async () => {
    await upload()
    accept(await api.draft(id, draftInput()))
    status.value = '录音已保存，可以回放或识别。'
  })
}

function transcribe() {
  return operate('识别中', async () => {
    await upload()
    accept(await api.draft(id, draftInput()))
    const result = await api.transcribe(id, { ...cursor(), audioId: audioId.value })
    draft.value = result.text.slice(0, 8000)
    status.value = '转写完成，请校对文字，再提交本题。'
  })
}

function speak() {
  return operate('播报中', async () => {
    resetSpeech()
    try {
      const blob = await api.speech(session.value.question.prompt)
      if (!alive) return
      readUrl.value = URL.createObjectURL(blob)
      ttsPlayer = new Audio(readUrl.value)
      await ttsPlayer.play().catch(() => {})
    } catch (e) {
      if (e.response?.data instanceof Blob) {
        try { e.response.data = JSON.parse(await e.response.data.text()) } catch { /* use generic request error */ }
      }
      throw e
    }
  })
}

function replaceAnswer(answer) {
  if (!alive || !session.value) return
  const items = session.value.answers
  session.value = { ...session.value, answers: items.some((a) => a.id === answer.id)
    ? items.map((a) => a.id === answer.id ? answer : a) : [...items, answer] }
}

async function evaluate(answerId) {
  if (evaluating.value[answerId]) return
  evaluating.value[answerId] = true
  try {
    replaceAnswer(await api.evaluate(answerId))
  } catch (e) {
    if (alive) {
      error.value = '回答已保存。' + message(e)
      try { replaceAnswer(await api.answer(answerId)) } catch { /* polling will retry */ }
    }
  } finally {
    delete evaluating.value[answerId]
  }
}

function submit() {
  if (!draft.value.trim() || recording.value) return
  return operate('正在提交', async () => {
    await upload()
    const input = { ...draftInput(), text: draft.value.trim() }
    const fingerprint = JSON.stringify(input)
    if (request?.fingerprint !== fingerprint) request = { fingerprint, key: crypto.randomUUID() }
    const result = await api.submit(id, { ...input, requestKey: request.key })
    replaceAnswer(result)
    session.value = { ...session.value, phase: 'REVIEW', clockRunning: false }
    status.value = '回答已保存。'
    void evaluate(result.id)
    try { accept(await api.progress(id)) } catch (e) { if (alive) error.value = '回答已保存。' + message(e) }
  })
}

async function tick() {
  if (!alive || !session.value) return
  await save()
  const running = records.value.filter((a) => a.evaluationState === 'RUNNING' && !evaluating.value[a.id])
  await Promise.allSettled(running.map(async (answer) => replaceAnswer(await api.answer(answer.id))))
  if (!busy.value && !answering.value) {
    try {
      const result = await api.progress(id)
      if (alive && !busy.value) {
        session.value = { ...session.value, pausedMs: result.pausedMs, asrMs: result.asrMs, evaluationMs: result.evaluationMs }
      }
    } catch { /* keep the most recent saved times */ }
  }
}

async function refresh() {
  try {
    if (saving) await saving.catch(() => {})
    const result = await api.progress(id)
    if (!alive) return
    accept(result)
    error.value = ''
  } catch (e) {
    if (alive) error.value = message(e)
  }
}

onMounted(async () => {
  await refresh()
  if (!alive) return
  if (session.value?.mode && session.value.mode !== props.mode) {
    await router.replace(sessionPath(session.value.mode, id))
    return
  }
  void save()
  api.voiceStatus().then((result) => { if (alive) services.value = result }).catch(() => {})
  heartbeat = setInterval(tick, 4000)
  ticker = setInterval(() => {
    if (session.value?.clockRunning && !busy.value && !document.hidden) {
      clock.value = session.value.effectiveMs + Math.min(12000, performance.now() - sampledAt)
    }
  }, 250)
})

onBeforeUnmount(() => {
  alive = false
  clearInterval(ticker)
  clearInterval(heartbeat)
  clearTimeout(saveTimer)
  resetSpeech()
})
</script>

<template>
  <main :id="special ? 'practice' : 'interview'" class="page-shell training-app page">
    <header class="page-heading training-heading"><div class="eyebrow">{{special ? 'FOCUSED PRACTICE' : 'PRACTICE ROOM'}}</div><h1>{{special ? '专项作答' : '普通作答'}}</h1></header>
    <div v-if="error" class="request-error">
      <ElAlert :title="error" type="error" :closable="false" show-icon />
      <ElButton v-if="!session" @click="refresh">重新加载</ElButton>
      <ElButton v-else-if="!recording && !busy" text @click="refresh">从服务器重新读取进度</ElButton>
    </div>
    <p v-if="!session && !error">正在读取训练进度…</p>
    <template v-if="session">
      <section id="interviewLive" class="panel">
        <div class="row-heading">
          <div>
            <span class="tag green">第 {{ session.position + 1 }} / {{ session.total }} 题</span>
            <span class="tag orange">有效作答 {{ duration(clock) }}</span>
            <span class="tag">{{ stateLabel(session.state) }}</span>
          </div>
          <span class="muted">{{ special ? '专项训练' : '普通训练' }} · 第 {{ session.roundNumber }} 轮</span>
        </div>
        <ElProgress :percentage="Math.round(tally / session.total * 100)" :format="() => tally + ' / ' + session.total" />
        <div class="bubble ai">
          <small>{{ session.question.topic }} · {{ session.question.type }}</small>
          <h2>{{ session.question.title }}</h2>
          <p>{{ session.question.prompt }}</p>
        </div>
        <div v-if="services?.ttsConfigured" class="actions">
          <ElButton :disabled="!!busy || recording" @click="speak">播报题目</ElButton>
          <audio v-if="readUrl" controls :src="readUrl" aria-label="题目播报" />
        </div>
        <p v-else class="muted">{{ services ? '语音播报未配置' : '语音播报状态暂不可用' }}</p>

        <template v-if="answering && session.state !== 'ENDED'">
          <div class="voice-panel">
            <h3>语音作答</h3>
            <VoiceRecorder ref="voice" :src="audioId ? audioPath(audioId) : undefined"
              :disabled="!active || !!busy" @recorded="onRecorded" @clear="clearAudio" @state="recordPhase = $event" />
            <div class="actions">
              <ElButton v-if="pendingAudio && !services?.asrConfigured" :disabled="!active || !!busy || recording"
                @click="saveAudio">上传录音</ElButton>
              <ElButton v-if="services?.asrConfigured" :disabled="!active || !!busy || recording || (!pendingAudio && !audioId)"
                @click="transcribe">识别 / 重试识别</ElButton>
            </div>
            <p v-if="!services?.asrConfigured" class="muted">
              {{ services ? '语音识别未配置' : '语音识别状态暂不可用' }}。可回放后手动填写回答，提交时关联原始录音。
            </p>
            <p v-else-if="busy === '识别中'" class="muted">正在转写录音。长录音可能需要等待接近录音时长。</p>
          </div>
          <div class="field">
            <label for="answer-text">文字作答 / 确认语音转写</label>
            <ElInput id="answer-text" v-model="draft" type="textarea" :rows="4" :maxlength="8000" show-word-limit
              placeholder="先说核心结论，再补充依据、边界条件和异常处理" :disabled="!active || !!busy" @input="scheduleSave" />
          </div>
        </template>

        <div class="actions">
          <ElButton v-if="active && answering" type="primary" :disabled="!!busy || recording || !draft.trim()" @click="submit">
            提交本题回答
          </ElButton>
          <ElButton v-if="active && !answering" type="success" :disabled="!!busy" @click="transition('redraft')">重答本题</ElButton>
          <ElButton v-if="active && !answering && !last" :disabled="!!busy" @click="transition('next')">下一题</ElButton>
          <ElButton v-if="active" :disabled="!!busy || recordPhase === 'permission'" @click="pause">暂停并保存</ElButton>
          <ElButton v-if="session.state === 'PAUSED'" type="success" :disabled="!!busy" @click="transition('resume')">继续训练</ElButton>
          <ElButton v-if="session.state !== 'ENDED'" text :disabled="!!busy || recording" @click="transition('end')">结束训练</ElButton>
          <RouterLink v-if="session.state === 'ENDED'" :to="`/reports/${id}`">查看本场报告</RouterLink>
          <RouterLink :to="special ? '/practice' : '/training'">返回选题</RouterLink>
        </div>
        <p v-if="busy || status" role="status">{{ busy || status }}</p>
        <p v-if="draftError && active && answering" class="notice" role="alert">{{ draftError }}</p>
        <p class="muted">暂停 {{ duration(session.pausedMs) }} · 识别等待 {{ duration(session.asrMs) }} · 评价等待 {{ duration(session.evaluationMs) }}</p>
      </section>

      <section v-if="comparison" class="panel">
        <h2>前后回答对比</h2>
        <div class="grid two">
          <div class="bubble ai"><small>来源回答 · {{ session.sourceAnswer.questionTitle }}</small>{{ session.sourceAnswer.text }}</div>
          <div class="bubble me"><small>复练回答 · {{ comparison.questionTitle }}</small>{{ comparison.text }}</div>
        </div>
      </section>
      <section v-if="records.length" class="panel">
        <h2>逐题反馈与作答记录</h2>
        <AnswerFeedback v-for="answer in records" :key="answer.id" :answer="answer" :evaluating="evaluating[answer.id]"
          @evaluate="evaluate" />
      </section>
    </template>
  </main>
</template>
