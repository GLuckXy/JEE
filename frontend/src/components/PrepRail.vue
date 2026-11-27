<script setup lang="ts">
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import type { Summary } from '../types'

const props = defineProps<{ records: readonly Summary[] }>()
const steps = ['上传简历', '校对确认', '选择项目']
const confirmedResume = computed(() => props.records.find(resume => resume.confirmStatus === 'CONFIRMED'))
const currentStep = computed(() => confirmedResume.value ? 3 : props.records.length ? 2 : 1)
</script>

<template>
  <div class="prep-rail">
    <ol class="prep-rail__steps" aria-label="准备流程">
      <li
        v-for="(label, index) in steps"
        :key="label"
        :aria-current="index + 1 === currentStep ? 'step' : undefined"
        :class="{ 'is-done': index + 1 < currentStep }"
      >
        <span class="prep-rail__marker num" aria-hidden="true">
          <el-icon v-if="index + 1 < currentStep"><Check /></el-icon>
          <template v-else-if="index + 1 === currentStep">{{ index + 1 }}</template>
        </span>
        <span class="prep-rail__label">{{ label }}<span v-if="index + 1 < currentStep" class="visually-hidden">（已完成）</span></span>
      </li>
    </ol>
    <router-link v-if="confirmedResume" class="prep-rail__action" :to="{ path: '/projects', query: { resumeId: confirmedResume.id } }">
      去选择项目 <span aria-hidden="true">→</span>
    </router-link>
  </div>
</template>
