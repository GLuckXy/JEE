<script setup>
import { computed, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElOption, ElSelect } from 'element-plus'
import { useRoute } from 'vue-router'
import { useAuth } from '../../stores/auth'
import { createFormalApi } from './formalApi.js'

const route = useRoute()
const auth = useAuth()
const api = createFormalApi()
const storageKey = `formal-interview.session.${auth.user?.id ?? ''}`
const options = ref(null)
const roleId = ref('')
const projectId = ref('')
const session = ref(null)
const error = ref('')
const busy = ref(false)
const recording = ref(false)
const pending = ref(null)
const now = ref(Date.now())
let clockOffset = 0
let stream
let recorder
let interval

const stages = { INTRODUCTION: '自我介绍', PROJECT: '项目深挖', TECHNICAL: '技术问答' }
const endings = { COMPLETED: '面试已完成', TIME_LIMIT: '整场时间已用完', EARLY_EXIT: '面试已提前结束', INTERRUPTED: '面试已标记为技术中断' }
const remaining = computed(() => {
  if (session.value?.state !== 'RECORDING') return 0
  return Math.max(0, session.value.turn.deadlineAt - (now.value + clockOffset))
})
const totalRemaining = computed(() => {
  if (!session.value) return 0
  if (session.value.state !== 'RECORDING') return session.value.remainingMillis
  const elapsed = Math.max(0, now.value + clockOffset - session.value.serverTime)
  const available = session.value.turn.deadlineAt - session.value.serverTime
  return Math.max(0, session.value.remainingMillis - Math.min(elapsed, available))
})

const turnSeconds = computed(() => Math.ceil(remaining.value / 1000))
const canRetryProcessing = computed(() => ['AUDIO_SAVED', 'NEEDS_FOLLOW_UP'].includes(session.value?.state))
const totalClock = computed(() => {
  const seconds = Math.ceil(totalRemaining.value / 1000)
  return Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0')
})

watch(() => session.value?.state, state => {
  document.documentElement.classList.toggle('is-focus', Boolean(state && state !== 'ENDED'))
})
onBeforeUnmount(() => document.documentElement.classList.remove('is-focus'))

function sync(value) {
  now.value = Date.now()
  clockOffset = value.serverTime - now.value
  session.value = value
  if (value.state === 'ENDED') {
    sessionStorage.removeItem(storageKey)
    releaseMicrophone()
  } else {
    sessionStorage.setItem(storageKey, value.id)
  }
}

async function perform(action) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (failure) {
    error.value = failure.message || '操作失败，请重试。'
  } finally {
    busy.value = false
  }
}

function releaseMicrophone() {
  stream?.getTracks().forEach((track) => track.stop())
  stream = undefined
  recorder = undefined
  recording.value = false
}

async function microphone() {
  if (stream?.getAudioTracks().some((track) => track.readyState === 'live')) return
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    throw new Error('当前环境无法录音，请使用支持录音的浏览器，并通过本机地址或 HTTPS 访问。')
  }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  } catch {
    throw new Error('麦克风无法使用。请检查授权和设备连接后重试。')
  }
}

function loadOptions() {
  return perform(async () => {
    options.value = await api.options()
    roleId.value = options.value.roles[0]?.id || ''
    projectId.value = options.value.projects.find((project) => project.id === route.query.projectId)?.id || options.value.projects[0]?.id || ''
    const saved = sessionStorage.getItem(storageKey)
    if (saved && !session.value) {
      try {
        sync(await api.get(saved))
      } catch (failure) {
        if (failure.status === 404) sessionStorage.removeItem(storageKey)
        else throw failure
      }
    }
  })
}

function create() {
  return perform(async () => {
    await microphone()
    try {
      sync(await api.create(roleId.value, projectId.value))
    } finally {
      releaseMicrophone()
    }
  })
}

function startRecording() {
  return perform(async () => {
    await microphone()
    const { id, turn } = session.value
    try {
      const types = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4']
      const mimeType = types.find((type) => MediaRecorder.isTypeSupported(type))
      if (!mimeType) throw new Error('浏览器不支持本模块接受的录音格式。')
      recorder = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: 64_000 })
      const chunks = []
      const activeRecorder = recorder
      recorder.ondataavailable = (event) => {
        if (event.data.size) chunks.push(event.data)
      }
      recorder.onstop = () => {
        pending.value = {
          id,
          turnId: turn.id,
          blob: new Blob(chunks, { type: activeRecorder.mimeType || mimeType }),
          finish: pending.value?.finish ?? false,
        }
        releaseMicrophone()
      }
      recorder.onerror = () => {
        error.value = '录音设备异常。可以保存已有录音，或将本场标记为技术中断。'
        recording.value = false
      }
      sync(await api.start(id, turn.id))
      if (session.value.state !== 'RECORDING' || session.value.turn.id !== turn.id) {
        releaseMicrophone()
        return
      }
      recorder.start(250)
      recording.value = true
    } catch (failure) {
      releaseMicrophone()
      throw failure
    }
  })
}

async function uploadPending(stoppedOnServer = false) {
  const { id, turnId, blob, finish } = pending.value
  if (!blob?.size) throw new Error('这次未采集到音频。可等待上传期限结束，或标记技术中断。')
  if (!stoppedOnServer) sync(await api.stop(id, turnId, finish))
  try {
    const value = await api.upload(id, turnId, blob)
    pending.value = null
    sync(value)
  } catch (failure) {
    const latest = await api.get(id).catch(() => null)
    if (latest) {
      if (latest.turn?.id !== turnId || ['AUDIO_SAVED', 'NEEDS_FOLLOW_UP'].includes(latest.state)) {
        pending.value = null
      }
      sync(latest)
    }
    throw failure
  }
}

function submitRecording(finish = false) {
  return perform(async () => {
    const { id, turn } = session.value
    pending.value = { id, turnId: turn.id, blob: null, finish }
    const stopped = new Promise((resolve) => {
      recorder.addEventListener('stop', resolve, { once: true })
      recorder.stop()
      recording.value = false
    })
    const freeze = api.stop(id, turn.id, finish).then(sync)
    await Promise.all([stopped, freeze])
    await uploadPending(true)
  })
}

const refresh = () => perform(async () => sync(await api.get(session.value.id)))
const retry = () => perform(async () => sync(await api.retry(session.value.id, session.value.turn.id)))
const skip = () => perform(async () => sync(await api.skipFollowUp(session.value.id, session.value.turn.id)))

function end(reason) {
  if (recording.value && reason === 'EARLY_EXIT') return submitRecording(true)
  return perform(async () => {
    sync(await api.end(session.value.id, reason))
    pending.value = null
  })
}

onMounted(() => {
  loadOptions()
  interval = setInterval(() => {
    now.value = Date.now()
    if (recording.value && remaining.value === 0) submitRecording()
    const current = session.value
    if (current?.state === 'AWAITING_AUDIO' && now.value + clockOffset > current.turn.uploadBy) {
      refresh()
    }
  }, 200)
})

onUnmounted(() => {
  clearInterval(interval)
  releaseMicrophone()
})
</script>

<template>
  <main class="page-shell formal">
    <header v-if="!session" class="page-heading">
      <div class="eyebrow">训练</div>
      <h1>正式模拟面试</h1>
      <p class="muted">请使用麦克风作答。整场按有效作答时间计时，提交后的首次录音会保留。</p>
    </header>

    <ElAlert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <section v-if="!session" class="surface formal-settings" aria-label="面试设置">
      <ElAlert
        v-if="options && !options.asrReady"
        class="formal-warning"
        type="warning"
        title="语音转写服务尚未配置"
        :closable="false"
        show-icon
      />
      <ElAlert
        v-if="options && !options.modelReady"
        class="formal-warning"
        type="warning"
        title="项目提问服务尚未配置"
        :closable="false"
        show-icon
      />
      <p v-if="options && (!options.asrReady || !options.modelReady)" class="muted">
        语音转写或项目提问服务暂未就绪，请稍后再试或联系管理员。
      </p>
      <div class="formal-choices">
        <label>目标岗位
          <ElSelect v-model="roleId" aria-label="目标岗位" placeholder="选择目标岗位">
            <ElOption v-for="role in options?.roles" :key="role.id" :label="role.name" :value="role.id" />
          </ElSelect>
        </label>
        <label>已确认项目
          <ElSelect v-model="projectId" aria-label="已确认项目" placeholder="选择已确认项目">
            <ElOption v-for="project in options?.projects" :key="project.id" :label="project.name" :value="project.id" />
          </ElSelect>
        </label>
      </div>
      <p v-if="options && !options.projects.length" class="muted">请先在 <RouterLink to="/resumes">简历管理</RouterLink> 中确认一个项目。</p>
      <div class="formal-actions">
        <ElButton type="primary" :loading="busy" :disabled="!roleId || !projectId || !options?.asrReady || !options?.modelReady" @click="create">
          检查麦克风并开始
        </ElButton>
        <ElButton :disabled="busy" @click="loadOptions">刷新选项</ElButton>
      </div>
    </section>

    <section v-else-if="session.state === 'ENDED'" class="surface formal-result" aria-live="polite">
      <h1>{{ endings[session.endReason] }}</h1>
      <p class="muted num">会话编号 {{ session.id }}</p>
      <div class="formal-actions">
        <RouterLink class="formal-report-link" :to="'/reports/' + session.id">查看本场报告</RouterLink>
        <ElButton @click="session = null">返回面试设置</ElButton>
      </div>
    </section>

    <section v-else class="surface formal-turn" aria-label="当前问题">
      <div class="formal-progress">
        <span class="tag green">{{ stages[session.turn.stage] }}{{ session.turn.followUp ? ' · 追问' : '' }}</span>
        <div class="session-tools">
          <span class="muted num">第 {{ session.index }} / {{ session.total }} 题</span>
          <ElDropdown trigger="click" placement="bottom-end" :disabled="busy">
            <ElButton text :disabled="busy">更多操作</ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem v-if="!recording" :disabled="busy" @click="refresh">刷新状态</ElDropdownItem>
                <ElDropdownItem v-if="canRetryProcessing && pending?.blob?.size" :disabled="busy" @click="perform(uploadPending)">
                  重试上传
                </ElDropdownItem>
                <ElDropdownItem v-if="!pending" :disabled="busy" @click="end('EARLY_EXIT')">提前结束</ElDropdownItem>
                <ElDropdownItem v-if="!recording && session.state !== 'READY'" :disabled="busy" @click="end('INTERRUPTED')">
                  标记技术中断
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
        </div>
      </div>
      <h1 class="formal-question">{{ session.turn.text || '开始录音后显示本题' }}</h1>

      <div class="formal-timer num">
        <span class="muted">本题剩余</span>
        <strong class="formal-timer__seconds" :class="{ 'is-urgent': recording && turnSeconds <= 10 }">
          {{ recording ? turnSeconds : '—' }}<span v-if="recording" class="formal-timer__unit"> 秒</span>
        </strong>
        <span class="muted">整场剩余 {{ totalClock }}</span>
      </div>

      <p v-if="recording" class="formal-recording" role="status">
        <span class="formal-recording__dot" aria-hidden="true"></span>录音中
      </p>
      <p v-else-if="busy" class="formal-status" role="status">正在处理，请稍候。</p>
      <p v-else-if="pending?.blob?.size" class="formal-status" role="status">录音已保留在本页，可以重试上传。</p>
      <p v-else-if="session.state === 'AUDIO_SAVED'" class="formal-status" role="status">录音已保存，可以重试转写。</p>
      <p v-else-if="session.state === 'NEEDS_FOLLOW_UP'" class="formal-status" role="status">回答已保存，可以重试生成追问或进入下一题。</p>
      <p v-else-if="['RECORDING', 'AWAITING_AUDIO'].includes(session.state)" class="formal-status" role="status">
        本页没有可提交的录音。可刷新会话状态，或标记技术中断。
      </p>

      <div class="formal-actions">
        <ElButton v-if="recording" type="primary" :disabled="busy" @click="submitRecording()">
          提交本题
        </ElButton>
        <ElButton v-else-if="session.state === 'READY' && !pending" type="primary" :disabled="busy" @click="startRecording">
          开始录音
        </ElButton>
        <ElButton v-else-if="canRetryProcessing" type="primary" :disabled="busy" @click="retry">
          重试处理
        </ElButton>
        <ElButton v-else-if="pending?.blob?.size" type="primary" :disabled="busy" @click="perform(uploadPending)">
          重试上传
        </ElButton>
        <ElButton v-if="session.state === 'NEEDS_FOLLOW_UP'" :disabled="busy" @click="skip">跳过追问</ElButton>
      </div>
    </section>
  </main>
</template>
