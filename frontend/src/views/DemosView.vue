<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {moduleRequest, showError} from '../api'
import type {Page} from '../types'

type Job = {id:number; name:string}
type Demo = {id:number; jobId:number; title:string; introduction:string}
type Detail = Demo & {sourceNote:string; stages:{id:number; sortOrder:number; title:string; interviewerQuestion:string; sampleAnswer:string; followUp:string; explanation:string}[]}
type Step = {order:number; title:string; purpose:string}
const jobs=ref<Job[]>([]), items=ref<Demo[]>([]), guide=ref<Step[]>([]), detail=ref<Detail>(), jobId=ref<number>(), page=ref(1), total=ref(0)
const busy=ref(false), error=ref('')
let revision=0

async function load() {
  const current=++revision
  busy.value=true; error.value=''; detail.value=undefined
  try {
    const result=await moduleRequest<Page<Demo>>('GET','/content/demos',undefined,{params:{jobId:jobId.value,page:page.value,size:12}})
    if(current===revision){items.value=result.records; total.value=result.total}
  }catch(failure){if(current===revision) error.value=failure instanceof Error?failure.message:'加载示范失败'}
  finally{if(current===revision) busy.value=false}
}

async function show(id:number){
  const current=++revision
  busy.value=true; error.value=''
  try {const result=await moduleRequest<Detail>('GET',`/content/demos/${id}`); if(current===revision) detail.value=result}
  catch(failure){if(current===revision) showError(failure)}
  finally{if(current===revision) busy.value=false}
}

onMounted(async()=>{
  void load()
  try{
    const [positions, steps]=await Promise.all([moduleRequest<Page<Job>>('GET','/content/jobs',undefined,{params:{size:100}}),moduleRequest<Step[]>('GET','/content/interview-guide')])
    jobs.value=positions.records; guide.value=steps
  }catch(failure){showError(failure)}
})
</script>

<template>
  <main class="page-shell">
    <header class="page-heading"><div class="eyebrow">准备</div><h1>典型示范</h1><p class="muted">从开场到追问，观察候选人如何组织一段有依据的回答。</p></header>
    <section v-if="guide.length" class="surface demo-guide" aria-label="面试流程">
      <article v-for="step in guide" :key="step.order"><span class="demo-step-number num">{{String(step.order).padStart(2,'0')}}</span><h3>{{step.title}}</h3><p class="muted">{{step.purpose}}</p></article>
    </section>
    <section class="surface demo-library" v-loading="busy" aria-label="典型示范">
      <div class="demo-toolbar"><h2>岗位示范</h2><el-select v-model="jobId" clearable placeholder="全部岗位" aria-label="按岗位筛选" @change="page=1;load()"><el-option v-for="job in jobs" :key="job.id" :value="job.id" :label="job.name"/></el-select></div>
      <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="load">重试</el-button></el-alert>
      <el-empty v-else-if="!busy && !items.length" description="暂时没有已发布的示范"/>
      <button v-for="item in items" :key="item.id" class="demo-row" :aria-pressed="detail?.id===item.id" @click="show(item.id)"><span><strong>{{item.title}}</strong><small>{{item.introduction}}</small></span><span aria-hidden="true">→</span></button>
      <el-pagination v-if="total>12" v-model:current-page="page" :page-size="12" :total="total" layout="prev, pager, next" @current-change="load"/>
    </section>
    <section v-if="detail" class="surface demo-library" aria-label="示范详情">
      <h2>{{detail.title}}</h2><p class="muted">{{detail.sourceNote}}</p>
      <article v-for="stage in detail.stages" :key="stage.id" class="demo-stage">
        <h3 class="num">{{stage.sortOrder}}. {{stage.title}}</h3><p><strong>面试官：</strong>{{stage.interviewerQuestion}}</p><p><strong>示范回答：</strong>{{stage.sampleAnswer}}</p>
        <p v-if="stage.followUp"><strong>追问：</strong>{{stage.followUp}}</p><p><strong>解读：</strong>{{stage.explanation}}</p>
      </article>
      <RouterLink to="/training" class="inline-link">用自己选的题练一次 →</RouterLink>
    </section>
  </main>
</template>
