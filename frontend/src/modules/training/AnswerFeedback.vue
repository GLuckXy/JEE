<script setup>
import { ElButton } from 'element-plus'
import { audioPath, duration } from './api'
import PrivateAudio from '../../components/PrivateAudio.vue'

defineProps({ answer: { type: Object, required: true }, evaluating: Boolean })
defineEmits(['evaluate'])
</script>

<template>
  <article class="answer-record">
    <div class="row-heading">
      <h3 class="num">第 {{ answer.position + 1 }} 题 · 第 {{ answer.roundNumber }} 轮</h3>
      <span class="tag num">{{ duration(answer.effectiveMs) }} 有效作答</span>
    </div>
    <dl class="feedback-list">
      <dt>原题</dt><dd><div class="bubble ai">{{ answer.prompt }}</div></dd>
      <dt>我的回答</dt><dd><div class="bubble me">{{ answer.text }}</div></dd>
      <template v-if="answer.audioId">
        <dt>原始录音</dt><dd><PrivateAudio :path="audioPath(answer.audioId)" label="本轮原始录音" /></dd>
      </template>
      <template v-if="answer.feedback">
        <dt>反馈类型</dt><dd><span class="tag">{{ answer.feedback.label }}</span></dd>
        <dt>参考要点</dt><dd><ul><li v-for="item in answer.feedback.referencePoints" :key="item">{{ item }}</li></ul></dd>
        <dt>已覆盖内容</dt><dd class="feedback-tags"><span v-if="!answer.feedback.covered.length">暂未匹配到参考要点</span>
          <span v-for="item in answer.feedback.covered" :key="item" class="tag green">{{ item }}</span></dd>
        <dt>遗漏内容</dt><dd class="feedback-tags"><span v-if="!answer.feedback.missing.length">参考要点均已匹配</span>
          <span v-for="item in answer.feedback.missing" :key="item" class="tag orange">{{ item }}</span></dd>
        <dt>改进建议</dt><dd>{{ answer.feedback.suggestion }}</dd>
      </template>
    </dl>
    <div v-if="!answer.feedback" class="actions" aria-live="polite">
      <p v-if="evaluating || answer.evaluationState === 'RUNNING'">回答已保存，正在评价…</p>
      <template v-else>
        <p v-if="answer.failureReason" class="notice">{{ answer.failureReason }}</p>
        <ElButton @click="$emit('evaluate', answer.id)">
          {{ answer.evaluationState === 'FAILED' ? '重试评价' : '获取评价' }}
        </ElButton>
      </template>
    </div>
  </article>
</template>
