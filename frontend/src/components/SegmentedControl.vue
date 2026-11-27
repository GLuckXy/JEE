<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{ options: readonly { value: string; label: string }[]; label: string }>()
const model = defineModel<string>({ required: true })
const group = ref<HTMLDivElement>()
const indicatorLeft = ref(0)
const indicatorWidth = ref(0)
let observer: ResizeObserver | undefined

function updateIndicator() {
  const selected = group.value?.querySelector<HTMLButtonElement>('button[aria-pressed="true"]')
  indicatorLeft.value = selected?.offsetLeft ?? 0
  indicatorWidth.value = selected?.offsetWidth ?? 0
}

function moveFocus(value: string, direction: number) {
  const current = props.options.findIndex(option => option.value === value)
  const next = (current + direction + props.options.length) % props.options.length
  model.value = props.options[next].value
  group.value?.querySelectorAll('button').item(next)?.focus()
}

watch([model, () => props.options], updateIndicator, { flush: 'post' })
onMounted(() => {
  updateIndicator()
  observer = new ResizeObserver(updateIndicator)
  if (group.value) observer.observe(group.value)
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div ref="group" class="segmented-control" role="group" :aria-label="label">
    <span
      class="segmented-control__indicator"
      aria-hidden="true"
      :style="{ width: indicatorWidth + 'px', transform: 'translateX(' + indicatorLeft + 'px)' }"
    />
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :aria-pressed="model === option.value"
      @click="model = option.value"
      @keydown.left.prevent="moveFocus(option.value, -1)"
      @keydown.right.prevent="moveFocus(option.value, 1)"
    >
      {{ option.label }}
    </button>
  </div>
</template>
