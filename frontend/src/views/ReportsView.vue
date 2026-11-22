<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {moduleRequest, showError} from '../api'
import type {Page} from '../types'
import PrivateAudio from '../components/PrivateAudio.vue'

type History = {sessionId:string; mode:string; endedAt:number; status:string}
type Score = {totalScore:number; source:string; coveredPoints:string; missingPoints:string; suggestions:string}
type Answer = {id:string; categoryId:number|null; topic:string|null; stage:string; followUp:boolean; position:number; round:number; question:string; referencePoints:string[]; text:string|null; audioId:string|null; effectiveMs:number; timedOut:boolean; status:string; evaluation:Score|null}
type Link = {id:string; targetSessionId:string}
type Report = History & {endReason:string; effectiveMs:number; questionCount:number; failureReason:string|null; answers:Answer[]; retrainLinks:Link[]; summary:{totalScore:number|null; dimensionScores:Record<string,number>; summary:string; weaknessTags:string; suggestions:string; answeredCount:number}|null}

const route=useRoute(), router=useRouter(), id=typeof route.params.id==='string'?route.params.id:''
const rows=ref<History[]>([]), report=ref<Report>(), page=ref(1), total=ref(0), mode=ref(''), busy=ref(false), retraining=ref(''), failure=ref('')
const modes:Record<string,string>={NORMAL:'普通训练',SPECIAL:'专项训练',FORMAL:'正式模拟'}
const ends:Record<string,string>={COMPLETED:'正常完成',TIME_LIMIT:'时间用完',EARLY_EXIT:'提前结束',INTERRUPTED:'技术中断'}
const labels:Record<string,string>={completeness:'完整性',accuracy:'准确性',structure:'结构',relevance:'相关性',expression:'表达'}
const states:Record<string,string>={COMPLETED:'报告已生成',FAILED:'可重试',PENDING:'等待生成'}
const stages:Record<string,string>={INTRODUCTION:'自我介绍',PROJECT:'项目深挖',TECHNICAL:'技术问答'}
const path=`/reports/sessions/${encodeURIComponent(id)}`
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
    <header class="page-heading"><div class="eyebrow">PRACTICE JOURNAL</div><h1>{{id?'回看这一场。':'报告与训练历史'}}</h1><p class="muted">按当时的题目和作答生成反馈。之后的题库调整不会改变已有报告。</p></header>
    <RouterLink v-if="id" class="inline-link" to="/reports">← 所有训练记录</RouterLink>
    <el-alert v-if="failure" type="error" :title="failure" :closable="false"><el-button text @click="id?loadReport():loadHistory()">重试读取</el-button></el-alert>
    <section v-if="!id" class="surface report-surface" v-loading="busy">
      <div class="report-row"><h2>已结束的会话</h2><el-select v-model="mode" clearable placeholder="全部类型" aria-label="训练类型" @change="page=1;loadHistory()"><el-option v-for="[key, label] in Object.entries(modes)" :key="key" :label="label" :value="key"/></el-select></div>
      <el-empty v-if="!busy&&!failure&&!rows.length" description="还没有结束的训练"><RouterLink to="/training" class="inline-link">去普通训练 →</RouterLink></el-empty>
      <RouterLink v-for="item in rows" :key="item.sessionId" class="report-row history-row" :to="`/reports/${item.sessionId}`"><span><strong>{{modes[item.mode]}}</strong><small>{{new Date(item.endedAt).toLocaleString('zh-CN')}}</small></span><span>{{states[item.status]}} →</span></RouterLink>
      <el-pagination v-if="total>12" v-model:current-page="page" :page-size="12" :total="total" layout="prev,pager,next" @current-change="loadHistory"/>
    </section>
    <template v-else-if="report">
      <section class="surface report-surface" v-loading="busy">
        <div class="report-row"><div><div class="eyebrow">{{modes[report.mode]}} · {{ends[report.endReason]}}</div><h2>整场概况</h2></div><strong v-if="report.summary?.totalScore!=null" class="report-score">{{report.summary.totalScore}} <small>分</small></strong></div>
        <template v-if="report.summary">
          <p>{{report.summary.summary}}</p><p class="muted">有效作答 {{report.summary.answeredCount}} / {{report.questionCount}} 题 · {{Math.floor(report.effectiveMs/1000)}} 秒</p>
          <div class="score-grid"><div v-for="[key,score] in Object.entries(report.summary.dimensionScores)" :key="key"><span>{{labels[key]}}</span> <strong>{{score}}</strong><el-progress :percentage="score" :show-text="false"/></div></div>
          <p>{{report.summary.suggestions}}</p>
        </template>
        <template v-else><p role="status">{{report.failureReason||'报告正在生成，作答已保存。'}}</p><el-button :loading="busy" @click="loadReport(true)">{{report.status==='FAILED'?'重试生成':'生成报告'}}</el-button></template>
      </section>
      <section v-if="report.answers.length" class="surface report-surface"><h2>逐题回顾</h2>
        <article v-for="answer in report.answers" :key="answer.id" class="answer-row">
          <div class="eyebrow">{{answer.topic||stages[answer.stage]}} {{answer.followUp?'· 追问':''}} {{report.mode!=='FORMAL'&&answer.round?`· 第 ${answer.round} 轮`:''}}</div><h3>{{answer.question}}</h3>
          <p class="answer-text">{{answer.text||(answer.status==='AUDIO_ONLY'?'仅保留录音，未计分':'未作答，未计分')}}</p><p class="fine-print">{{Math.floor(answer.effectiveMs/1000)}} 秒 {{answer.timedOut?'· 已超时':''}}</p>
          <PrivateAudio v-if="answer.audioId" :path="report.mode==='FORMAL'?`${path}/audio/${answer.audioId}`:`/voice/audio/${answer.audioId}`"/>
          <template v-if="answer.evaluation"><p><strong>{{answer.evaluation.totalScore}} 分</strong> · {{answer.evaluation.source==='AI'?'AI 评价':'规则评价'}}</p><p v-if="answer.evaluation.missingPoints" class="muted">遗漏：{{answer.evaluation.missingPoints}}</p><p>{{answer.evaluation.suggestions}}</p>
            <details v-if="answer.referencePoints.length"><summary>参考要点</summary><ul><li v-for="point in answer.referencePoints" :key="point">{{point}}</li></ul></details>
            <el-button v-if="answer.categoryId" class="retrain" :loading="retraining===answer.id" :disabled="!!retraining" @click="retrain(answer.id)">练习同类题 →</el-button>
          </template>
        </article>
      </section>
      <section v-if="report.retrainLinks.length" class="surface report-surface"><h2>从本报告开始的复练</h2><RouterLink v-for="(link,index) in report.retrainLinks" :key="link.id" :to="`/practice/${link.targetSessionId}`" class="history-row">打开复练 {{report.retrainLinks.length-index}} →</RouterLink></section>
    </template>
    <el-skeleton v-else-if="busy" animated :rows="5"/>
  </main>
</template>

<style scoped>
.report-page{display:grid;gap:16px}.report-page .page-heading{margin-bottom:0}.report-surface{padding:clamp(16px,4vw,28px)}
.report-row{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px}.report-row .el-select{width:200px}.history-row{display:flex;padding:14px 0;border-bottom:1px solid var(--line);text-decoration:none;color:inherit}.history-row:hover{color:#176959}.history-row small{display:block;color:var(--muted);margin-top:4px}
.report-score{font-size:2.25rem}.report-score small{font-size:1rem}.score-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(100px,1fr));gap:16px;margin:18px 0}.score-grid strong{float:right}.score-grid .el-progress{margin-top:5px}.answer-row{padding:18px 0;border-top:1px solid var(--line);overflow-wrap:anywhere}.answer-text{white-space:pre-wrap}.retrain{margin-top:12px}
</style>
