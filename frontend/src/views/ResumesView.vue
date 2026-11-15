<script setup lang="ts">
import {onMounted,ref,computed} from 'vue'
import {Upload,Lock,Right,Document} from '@element-plus/icons-vue'
import {api,showError} from '../api'
import {dateTime} from '../types'
import type {Summary} from '../types'
import UploadDialog from '../components/UploadDialog.vue'
import DeleteResumeButton from '../components/DeleteResumeButton.vue'
const loading=ref(true),error=ref(''),records=ref<Summary[]>([]),total=ref(0),page=ref(1),filter=ref('all'),uploadOpen=ref(false)
const visible=computed(()=>records.value.filter(r=>filter.value==='all'||r.confirmStatus===filter.value))
async function load(){loading.value=true;error.value='';try{const result=await api.resumes(page.value,8);records.value=result.records;total.value=result.total}catch(e){error.value=e instanceof Error?e.message:'加载失败';showError(e)}finally{loading.value=false}}
async function afterDelete(){if(records.value.length===1&&page.value>1)page.value--;await load()}
onMounted(load)
</script>
<template>
  <main>
    <section class="home-hero">
      <div class="eyebrow">YOUR NEXT CHAPTER STARTS HERE</div>
      <h1>整理你的经历。<br><span class="highlight">准备好，迎接下一次机会。</span></h1>
      <p>从一份简历开始，把真实的项目经历整理清楚。<br>让每一次面试，都更有底气。</p>
      <el-button class="yellow-button hero-button" :icon="Upload" @click="uploadOpen=true">上传我的简历</el-button>
      <div class="fine-print format-note">支持 PDF、Word · 单份最大 10 MB</div>
      <div class="preparation-steps" aria-label="准备流程">
        <span class="current"><b>1</b>上传简历</span><i aria-hidden="true">→</i>
        <span><b>2</b>校对并确认</span><i aria-hidden="true">→</i><span><b>3</b>选择项目</span>
      </div>
    </section>
    <section class="resume-library">
      <div class="section-head">
        <h2>我的简历 <span class="count-badge">{{total}}</span></h2>
        <div class="filter-pills" role="group" aria-label="当前页状态筛选">
          <button v-for="item in [{value:'all',label:'全部'},{value:'DRAFT',label:'待校对'},{value:'CONFIRMED',label:'已确认'}]" :key="item.value" :class="{selected:filter===item.value}" :aria-pressed="filter===item.value" @click="filter=item.value">{{item.label}}</button>
        </div>
      </div>
      <p v-if="total>8" class="fine-print">状态筛选作用于当前页，全部 {{total}} 份简历可通过分页查看。</p>
      <el-skeleton v-if="loading" :rows="5" animated class="loading-block"/>
      <el-result v-else-if="error" icon="warning" title="暂时无法加载简历" :sub-title="error"><template #extra><el-button @click="load">重新加载</el-button></template></el-result>
      <div v-else-if="visible.length" class="resume-grid">
        <article v-for="resume in visible" :key="resume.id" class="resume-card">
          <div class="card-top">
            <div class="document-icon" aria-hidden="true"><el-icon><Document/></el-icon><span>{{resume.originalName.split('.').pop()?.toUpperCase()}}</span></div>
            <span class="status-pill" :class="{confirmed:resume.confirmStatus==='CONFIRMED',failed:resume.parseStatus==='FAILED'&&resume.confirmStatus!=='CONFIRMED'}">{{resume.confirmStatus==='CONFIRMED'?'已确认':resume.parseStatus==='FAILED'?'待手动补充':'待校对'}}</span>
          </div>
          <h3 :title="resume.originalName">{{resume.originalName.replace(/\.[^.]+$/,'')}}</h3>
          <p class="file-name">{{resume.originalName}}</p>
          <p class="meta">{{dateTime(resume.createdAt)}} 上传</p>
          <div class="card-bottom">
            <p class="card-hint">{{resume.confirmStatus==='CONFIRMED'?`已确认 V${resume.version}，可以选择项目继续准备。`:resume.parseStatus==='SUCCESS'?'已提取文字，请核对项目名称与本人职责。':resume.parseStatus==='FAILED'?'原文件已保留，可以参照原文手动填写。':'文件正在处理中，请稍后刷新查看。'}}</p>
            <div class="card-actions">
              <router-link v-if="resume.confirmStatus==='CONFIRMED'" class="card-primary is-ready" :to="{path:'/projects',query:{resumeId:resume.id}}">选择项目<el-icon aria-hidden="true"><Right/></el-icon></router-link>
              <router-link v-else class="card-primary" :to="`/resumes/${resume.id}`">继续校对<el-icon aria-hidden="true"><Right/></el-icon></router-link>
              <router-link v-if="resume.confirmStatus==='CONFIRMED'" class="card-secondary" :to="`/resumes/${resume.id}`" aria-label="查看详情与版本" title="查看详情与版本">查看详情</router-link>
              <DeleteResumeButton :resume="resume" compact @deleted="afterDelete"/>
            </div>
          </div>
        </article>
      </div>
      <el-empty v-else :description="records.length?'本页暂无该状态的简历':'还没有简历。从你的第一份经历开始。'"><el-button v-if="!records.length" class="yellow-button" @click="uploadOpen=true">上传第一份简历</el-button><el-button v-else @click="filter='all'">查看全部</el-button></el-empty>
      <div class="section-foot"><span class="privacy"><el-icon><Lock/></el-icon>简历与项目资料，仅对你本人开放。</span><el-button text :loading="loading" @click="load">刷新列表 ↻</el-button></div>
      <el-pagination v-if="total>8" v-model:current-page="page" :page-size="8" :total="total" layout="prev, pager, next" @current-change="load"/>
    </section>
    <section class="closing-note"><div class="asterisk" aria-hidden="true">✳</div><h2>真实的细节，就是你的竞争力。</h2><p>你负责了什么，遇到了什么困难，又是怎样解决的。<br>确认前，花一点时间，让每一段经历准确表达自己。</p></section>
    <UploadDialog v-model="uploadOpen"/>
  </main>
</template>

