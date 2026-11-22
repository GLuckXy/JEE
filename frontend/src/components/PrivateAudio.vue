<script setup lang="ts">
import {onBeforeUnmount, ref, watch} from 'vue'
import {moduleRequest} from '../api'

const props = defineProps<{path:string; label?:string}>()
const url = ref(''), loading = ref(false), error = ref('')
let revision = 0

function clear() {
  revision++
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
  error.value = ''
  loading.value = false
}

async function load() {
  const current = ++revision
  loading.value = true
  error.value = ''
  try {
    const blob = await moduleRequest<Blob>('GET', props.path, undefined, {responseType:'blob'})
    if (current === revision) url.value = URL.createObjectURL(blob)
  } catch (failure) {
    if (current === revision) error.value = failure instanceof Error ? failure.message : '暂时无法读取录音'
  } finally {
    if (current === revision) loading.value = false
  }
}
watch(() => props.path, clear)
onBeforeUnmount(clear)
</script>

<template>
  <div>
    <audio v-if="url" :src="url" :aria-label="label || '私有录音'" controls preload="metadata" />
    <el-button v-else size="small" :loading="loading" @click="load">加载原始录音</el-button>
    <p v-if="error" role="alert" class="fine-print">{{error}}</p>
  </div>
</template>

<style scoped>
audio { display:block; max-width:100%; }
</style>
