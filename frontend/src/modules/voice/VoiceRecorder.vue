<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElButton } from 'element-plus'
import PrivateAudio from '../../components/PrivateAudio.vue'

const props = defineProps({ src: String, disabled: Boolean })
const emit = defineEmits(['recorded', 'clear', 'state'])
const phase = ref('idle')
const elapsed = ref(0)
const error = ref('')
const localUrl = ref('')
const playback = computed(() => localUrl.value || props.src)
let recorder
let stream
let stopping
let resolveStop
let timer
let startedAt
let disposed = false
const formats = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4', 'audio/webm']
const extensions = { 'audio/webm': 'webm', 'audio/ogg': 'ogg', 'audio/mp4': 'm4a' }

function setPhase(value) {
  phase.value = value
  emit('state', value)
}

function release() {
  clearInterval(timer)
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
}

function clearPreview() {
  if (localUrl.value) URL.revokeObjectURL(localUrl.value)
  localUrl.value = ''
  elapsed.value = 0
}

async function start() {
  if (phase.value !== 'idle' || props.disabled) return
  error.value = ''
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
    error.value = '录音需要支持 MediaRecorder 的浏览器，请使用 localhost 或 HTTPS 打开。'
    return
  }
  const mimeType = formats.find((type) => MediaRecorder.isTypeSupported(type))
  if (!mimeType) {
    error.value = '当前浏览器没有可用的录音格式。'
    return
  }
  setPhase('permission')
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    if (disposed) {
      release()
      return
    }
    const current = new MediaRecorder(stream, { mimeType })
    const chunks = []
    current.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data) }
    current.onerror = () => { error.value = '录音中断，将尝试保留已录片段。'; void stop() }
    current.onstop = () => {
      release()
      recorder = null
      if (!disposed) {
        elapsed.value = Math.max(0, performance.now() - startedAt)
        const type = current.mimeType || mimeType
        const file = new File(chunks, 'answer.' + extensions[type.split(';')[0]], { type })
        if (file.size) {
          localUrl.value = URL.createObjectURL(file)
          emit('recorded', { file, durationMs: elapsed.value })
        } else {
          error.value = '没有录到可用音频，请重录或使用文字作答。'
        }
        setPhase('idle')
      }
      resolveStop?.()
      stopping = null
      resolveStop = null
    }
    current.start(250)
    recorder = current
    clearPreview()
    emit('clear')
    startedAt = performance.now()
    setPhase('recording')
    timer = setInterval(() => {
      elapsed.value = performance.now() - startedAt
      if (elapsed.value >= 600000) void stop()
    }, 250)
  } catch (e) {
    release()
    if (disposed) return
    error.value = e.name === 'NotAllowedError' || e.name === 'SecurityError'
      ? '麦克风权限被拒绝，请在浏览器站点设置中授权，或使用文字作答。'
      : '无法启用麦克风，请检查设备连接或使用文字作答。'
    setPhase('idle')
  }
}

function stop() {
  if (stopping) return stopping
  if (!recorder || recorder.state === 'inactive') return Promise.resolve()
  setPhase('stopping')
  stopping = new Promise((resolve) => { resolveStop = resolve })
  recorder.stop()
  return stopping
}

function discard() {
  clearPreview()
  error.value = ''
  emit('clear')
}

watch(() => props.src, (src) => {
  if (src && localUrl.value) {
    URL.revokeObjectURL(localUrl.value)
    localUrl.value = ''
  }
})

onBeforeUnmount(() => {
  disposed = true
  if (recorder && recorder.state !== 'inactive') recorder.stop()
  release()
  clearPreview()
})

defineExpose({ stop })
</script>

<template>
  <div class="voice-recorder">
    <div class="voice-controls">
      <ElButton v-if="phase === 'recording'" type="warning" @click="stop">结束录音</ElButton>
      <ElButton v-else :disabled="disabled || phase !== 'idle'" @click="start">
        {{ playback ? '重新录音' : '开始录音' }}
      </ElButton>
      <span aria-live="polite">
        {{ { permission: '等待麦克风授权', recording: '录音中', stopping: '正在保留录音片段' }[phase] ||
          (playback ? '录音可回放' : '尚未开始录音') }}
        <template v-if="elapsed"> · {{ (elapsed / 1000).toFixed(1) }} 秒</template>
      </span>
      <ElButton v-if="playback && phase === 'idle'" text :disabled="disabled" @click="discard">移除录音</ElButton>
    </div>
    <p v-if="error" role="alert" class="voice-error">{{ error }}</p>
    <audio v-if="localUrl" :src="localUrl" controls preload="metadata" aria-label="我的录音" />
    <PrivateAudio v-else-if="src" :path="src" label="我的录音" />
  </div>
</template>

<style scoped>
.voice-controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.voice-controls > span { color: var(--muted); font-size: var(--fs-small); font-variant-numeric: tabular-nums; }
.voice-error { color: var(--danger); }
audio { display: block; margin-top: 12px; max-width: 100%; }
</style>
