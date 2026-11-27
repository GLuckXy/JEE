<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {moduleRequest, showError} from '../api'
import type {Page} from '../types'
import PrivateAudio from '../components/PrivateAudio.vue'
import SegmentedControl from '../components/SegmentedControl.vue'

type History = {sessionId:string; mode:string; endedAt:number; status:string}
type Score = {totalScore:number; source:string; coveredPoints:string; missingPoints:string; suggestions:string}
type Answer = {id:string; categoryId:number|null; topic:string|null; stage:string; followUp:boolean; position:number; round:number; question:string; referencePoints:string[]; text:string|null; audioId:string|null; effectiveMs:number; timedOut:boolean; status:string; evaluation:Score|null}
type Link = {id:string; targetSessionId:string}
type Report = History & {endReason:string; effectiveMs:number; questionCount:number; failureReason:string|null; answers:Answer[]; retrainLinks:Link[]; summary:{totalScore:number|null; dimensionScores:Record<string,number>; summary:string; weaknessTags:string; suggestions:string; answeredCount:number}|null}

const route=useRoute(), router=useRouter(), id=typeof route.params.id==='string'?route.params.id:''
const rows=ref<History[]>([]), report=ref<Report>(), page=ref(1), total=ref(0), mode=ref(''), busy=ref(false), retraining=ref(''), failure=ref('')
const modes:Record<string,string>={NORMAL:'普通训练',SPECIAL:'专项训练',FORMAL:'正式模拟'}
const modeOptions = [
  { value: '', label: '全部类型' },
  ...Object.entries(modes).map(([value, label]) => ({ value, label })),
]
const ends:Record<string,string>={COMPLETED:'正常完成',TIME_LIMIT:'时间用完',EARLY_EXIT:'提前结束',INTERRUPTED:'技术中断'}
const labels:Record<string,string>={completeness:'完整性',accuracy:'准确性',structure:'结构',relevance:'相关性',expression:'表达'}
const states:Record<string,string>={COMPLETED:'报告已生成',FAILED:'可重试',PENDING:'等待生成'}
const stages:Record<string,string>={INTRODUCTION:'自我介绍',PROJECT:'项目深挖',TECHNICAL:'技术问答'}
const path=`/reports/sessions/${encodeURIComponent(id)}`
const dimensions = computed(() => Object.entries(report.value?.summary?.dimensionScores ?? {}))
const lowestDimension = computed(() =>
  dimensions.value.reduce<[string, number] | undefined>(
    (lowest, entry) => !lowest || entry[1] < lowest[1] ? entry : lowest,
    undefined,
  )?.[0],
)

function formatDuration(milliseconds: number) {
  const seconds = Math.floor(milliseconds / 1000)
  return Math.floor(seconds / 60) + ' 分 ' + seconds % 60 + ' 秒'
}

let timer:ReturnType<typeof setTimeout>|undefined, disposed=false, remainingChecks=8

async function loadHistory(){
  busy.value=true; failure.value=''
  try{const value=await moduleRequest<Page<History>>('GET','/reports/history',undefined,{params:{mode:mode.value||undefined,page:page.value,size:12}});rows.value=value.records;total.value=value.total}
  catch(error){failure.value=error instanceof Error?error.message:'历史加载失败'}
  finally{busy.value=false}
}

async function loadReport(generate=false, quiet=false){
  if(timer)clearTimeout(timer)
  if(!quiet)busy.value=true
  failure.value=''
  try{
    report.value=await moduleRequest<Report>(generate?'POST':'GET',path+(generate?'/generate':''),undefined,{timeout:generate?240000:30000})
    if(!disposed&&report.value.status==='PENDING'&&remainingChecks-->0)timer=setTimeout(()=>void loadReport(false,true),1500)
  }catch(error){failure.value=error instanceof Error?error.message:'报告加载失败'}
  finally{if(!quiet)busy.value=false}
}

async function retrain(answerId:string){
  retraining.value=answerId
  try{const link=await moduleRequest<Link>('POST',`${path}/retrain`,{answerId}); await router.push(`/practice/${link.targetSessionId}`)}
  catch(error){showError(error)}finally{retraining.value=''}
}

onMounted(()=>id?void loadReport():void loadHistory())
onBeforeUnmount(()=>{disposed=true;if(timer)clearTimeout(timer)})
</script>

<template>
  <main class="page-shell report-page">
    <header class="page-heading">
      <div class="eyebrow">复盘</div>
      <h1>{{ id ? '回看这一场。' : '报告与训练历史' }}</h1>
      <p class="muted">按当时的题目和作答生成反馈。之后的题库调整不会改变已有报告。</p>
    </header>
    <RouterLink v-if="id" class="back-link report-back" to="/reports">← 所有训练记录</RouterLink>
    <el-alert v-if="failure" type="error" :title="failure" :closable="false">
      <el-button text @click="id ? loadReport() : loadHistory()">重试读取</el-button>
    </el-alert>

    <section v-if="!id" class="surface report-history" v-loading="busy">
      <div class="report-history__header">
        <h2>已结束的会话</h2>
        <SegmentedControl
          v-model="mode"
          :options="modeOptions"
          label="训练类型"
          @update:model-value="page = 1; loadHistory()"
        />
      </div>
      <el-empty v-if="!busy && !failure && !rows.length" description="还没有结束的训练">
        <RouterLink to="/training" class="inline-link">去普通训练 →</RouterLink>
      </el-empty>
      <RouterLink
        v-for="item in rows"
        :key="item.sessionId"
        class="report-history__row"
        :to="'/reports/' + item.sessionId"
      >
        <span class="tag">{{ modes[item.mode] }}</span>
        <time class="report-history__date num" :datetime="new Date(item.endedAt).toJSON() ?? undefined">{{ new Date(item.endedAt).toLocaleString('zh-CN') }}</time>
        <span class="report-history__status">
          <span class="status-pill" :class="{ confirmed: item.status === 'COMPLETED', failed: item.status === 'FAILED' }">{{ states[item.status] }}</span>{{ ' ' }}<span aria-hidden="true">→</span>
        </span>
      </RouterLink>
      <el-pagination v-if="total > 12" v-model:current-page="page" :page-size="12" :total="total" layout="prev, pager, next" @current-change="loadHistory" />
    </section>

    <template v-else-if="report">
      <section class="surface report-overview" v-loading="busy">
        <div class="report-context"><span class="tag">{{ modes[report.mode] }} · {{ ends[report.endReason] }}</span></div>
        <h2>整场概况</h2>
        <template v-if="report.summary">
          <div class="report-overview__summary">
            <strong v-if="report.summary.totalScore != null" class="report-score num">
              {{ report.summary.totalScore }} <span>分</span>
            </strong>
            <p>{{ report.summary.summary }}</p>
          </div>
          <p class="muted num">
            有效作答 {{ report.summary.answeredCount }} / {{ report.questionCount }} 题 · 用时 {{ formatDuration(report.effectiveMs) }}
          </p>

          <ul class="report-dimensions" aria-label="五维评分">
            <li
              v-for="([key, score], index) in dimensions"
              :key="key"
              class="report-dimension"
              :class="{ 'is-lowest': key === lowestDimension }"
            >
              <span class="report-dimension__label">
                {{ labels[key] ?? key }}<small v-if="key === lowestDimension" class="tag orange">优先加强</small>
              </span>
              <div
                class="report-dimension__track"
                role="progressbar"
                :aria-label="labels[key] ?? key"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-valuenow="score"
              >
                <span class="report-dimension__bar" :style="{ '--dimension-score': score + '%', '--dimension-delay': index * 60 + 'ms' }" />
              </div>
              <strong class="report-dimension__score num">{{ score }}</strong>
            </li>
          </ul>

          <blockquote v-if="report.summary.suggestions" class="surface-sunken report-suggestions">{{ report.summary.suggestions }}</blockquote>
        </template>
        <template v-else>
          <p role="status">{{ report.failureReason || '报告正在生成，作答已保存。' }}</p>
          <el-button :loading="busy" @click="loadReport(true)">{{ report.status === 'FAILED' ? '重试生成' : '生成报告' }}</el-button>
        </template>
      </section>

      <section v-if="report.answers.length" class="report-answers">
        <h2>逐题回顾</h2>
        <article v-for="answer in report.answers" :key="answer.id" class="surface report-answer">
          <div class="report-answer__context num">
            {{ answer.topic || stages[answer.stage] }} {{ answer.followUp ? '· 追问' : '' }}
            {{ report.mode !== 'FORMAL' && answer.round ? '· 第 ' + answer.round + ' 轮' : '' }}
          </div>
          <h3>{{ answer.question }}</h3>
          <p class="surface-sunken report-answer__text">
            {{ answer.text || (answer.status === 'AUDIO_ONLY' ? '仅保留录音，未计分' : '未作答，未计分') }}
          </p>
          <div class="report-answer__details">
            <strong v-if="answer.evaluation" class="num">{{ answer.evaluation.totalScore }} 分</strong>
            <span v-if="answer.evaluation">{{ answer.evaluation.source === 'AI' ? 'AI 评价' : '规则评价' }}</span>
            <span class="num">{{ formatDuration(answer.effectiveMs) }} {{ answer.timedOut ? '· 已超时' : '' }}</span>
            <span v-if="answer.evaluation?.missingPoints">遗漏：{{ answer.evaluation.missingPoints }}</span>
          </div>
          <PrivateAudio v-if="answer.audioId" :path="report.mode === 'FORMAL' ? path + '/audio/' + answer.audioId : '/voice/audio/' + answer.audioId" />
          <template v-if="answer.evaluation">
            <p>{{ answer.evaluation.suggestions }}</p>
            <details v-if="answer.referencePoints.length">
              <summary>参考要点</summary>
              <ul><li v-for="point in answer.referencePoints" :key="point">{{ point }}</li></ul>
            </details>
            <el-button v-if="answer.categoryId" :loading="retraining === answer.id" :disabled="!!retraining" @click="retrain(answer.id)">练习同类题 →</el-button>
          </template>
        </article>
      </section>

      <section v-if="report.retrainLinks.length" class="surface report-retrain">
        <h2>从本报告开始的复练</h2>
        <RouterLink v-for="(link, index) in report.retrainLinks" :key="link.id" :to="'/practice/' + link.targetSessionId" class="report-history__row num">
          打开复练 {{ report.retrainLinks.length - index }} →
        </RouterLink>
      </section>
    </template>
    <el-skeleton v-else-if="busy" animated :rows="5" />
  </main>
</template>
