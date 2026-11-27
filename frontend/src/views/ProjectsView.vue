<script setup lang="ts">
import {ref,onMounted,watch,onBeforeUnmount} from 'vue'
import {RouterLink,useRoute} from 'vue-router'
import {ElMessage} from 'element-plus'
import {Check,Download,Right,Select} from '@element-plus/icons-vue'
import {api,showError,ApiError} from '../api'
import type {Summary,Project,Selection} from '../types'
const route=useRoute(),resumes=ref<Summary[]>([]),resumeId=ref(''),projects=ref<Project[]>([]),current=ref<Project>(),selection=ref<Selection>(),loading=ref(true),busy=ref(false),error=ref(''),listError=ref(''),page=ref(1),total=ref(0)
let requestId=0
const labels={goal:'项目目标',responsibility:'本人职责',difficulty:'遇到的困难',solution:'解决过程',result:'结果与效果',improvement:'后续改进'} as const
async function loadResumes(){loading.value=true;listError.value='';try{const result=await api.resumes(page.value,20);resumes.value=result.records;total.value=result.total;if(!resumeId.value){const requested=route.query.resumeId;resumeId.value=typeof requested==='string'?requested:resumes.value.find(r=>r.confirmStatus==='CONFIRMED')?.id||''}}catch(e){listError.value=e instanceof Error?e.message:'加载失败';showError(e)}finally{loading.value=false}}
async function loadProjects(){const request=++requestId;projects.value=[];current.value=undefined;selection.value=undefined;error.value='';if(!resumeId.value)return;busy.value=true;try{const result=await api.projects(resumeId.value);if(request===requestId){projects.value=result;current.value=result[0]}}catch(e){if(request===requestId)error.value=e instanceof Error?e.message:'加载失败'}finally{if(request===requestId)busy.value=false}}
async function select(){if(!current.value)return;busy.value=true;try{selection.value=await api.select(resumeId.value,current.value.id,current.value.version);ElMessage.success('已选择这个项目')}catch(e){showError(e);if(e instanceof ApiError&&['VERSION_CONFLICT','PROJECT_NOT_CONFIRMED','RESUME_NOT_CONFIRMED'].includes(e.code))await loadProjects()}finally{busy.value=false}}
function exportSnapshot(){if(!selection.value)return;const blob=new Blob([JSON.stringify(selection.value,null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`面序-项目快照-V${selection.value.version}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
watch(resumeId,loadProjects)
onMounted(loadResumes)
onBeforeUnmount(()=>requestId++)
</script>
<template>
  <main class="page-shell">
    <header class="page-heading">
      <div class="eyebrow">准备</div>
      <h1>项目资料</h1>
      <p class="muted">选择已确认简历中的项目，回顾职责、技术选择与解决问题的过程。</p>
    </header>
    <section class="surface project-selector">
      <div>
        <h2>选择一份简历</h2>
        <p class="fine-print">只有已确认的项目可以被选用。</p>
      </div>
      <el-select v-model="resumeId" aria-label="选择已确认简历" placeholder="选择已确认简历" :loading="loading" :disabled="busy" class="resume-select">
        <el-option v-if="resumeId&&!resumes.some(r=>r.id===resumeId)" :value="resumeId" label="当前简历"/>
        <el-option v-for="resume in resumes" :key="resume.id" :label="`${resume.originalName} · ${resume.confirmStatus==='CONFIRMED'?'V'+resume.version:'待确认'}`" :value="resume.id" :disabled="resume.confirmStatus!=='CONFIRMED'"/>
      </el-select>
      <el-pagination v-if="total>20" v-model:current-page="page" :page-size="20" :total="total" small layout="prev, pager, next" @current-change="loadResumes"/>
    </section>
    <el-alert v-if="listError" :title="listError" type="error" :closable="false">
      <el-button text @click="loadResumes">重新加载</el-button>
    </el-alert>
    <el-skeleton v-if="busy&&!current" :rows="7" animated class="loading-block"/>
    <el-result v-else-if="error" icon="warning" title="暂时无法读取项目" :sub-title="error">
      <template #extra>
        <el-button @click="loadProjects">重试</el-button>
        <router-link :to="`/resumes/${resumeId}`" class="inline-link">返回简历校对</router-link>
      </template>
    </el-result>
    <div v-else-if="current" class="projects-layout">
      <aside class="surface project-list" aria-label="项目列表">
        <h2 class="project-list__title">项目列表 <span class="num">{{projects.length}}</span></h2>
        <button v-for="(project,index) in projects" :key="project.id" class="project-choice" :class="{active:current.id===project.id}" :disabled="busy" @click="current=project;selection=undefined">
          <span class="project-number num" aria-hidden="true">{{String(index+1).padStart(2,'0')}}</span>
          <span>{{project.name}}<small class="num">已确认 · V{{project.version}}</small></span>
          <el-icon v-if="current.id===project.id" class="project-check" aria-hidden="true">
            <Select/>
          </el-icon>
          <el-icon v-else aria-hidden="true">
            <Right/>
          </el-icon>
        </button>
      </aside>
      <section class="surface project-detail">
        <span class="status-pill confirmed">已确认 · V{{current.version}}</span>
        <h2>{{current.name}}</h2>
        <div class="tags">
          <el-tag class="tag" v-for="tech in current.techStack" :key="tech" type="info">{{tech}}</el-tag>
        </div>
        <dl class="project-fields">
          <div v-for="(label,key) in labels" :key="key" class="project-field" :class="{'is-wide':key==='goal'||key==='responsibility'}">
            <dt>{{label}}</dt>
            <dd :class="{'is-empty':!current[key]}">{{current[key]||'暂未填写'}}</dd>
          </div>
        </dl>
        <div class="form-footer">
          <span class="fine-print">按你确认过的真实内容提供。</span>
          <el-button type="primary" :loading="busy" :disabled="!!selection" :icon="selection?Check:undefined" @click="select">{{selection?'已选择此项目':'选择这个项目'}}<el-icon v-if="!selection" aria-hidden="true"><Right/></el-icon></el-button>
        </div>
        <div v-if="selection" class="surface-sunken selection-result">
          <div role="status">
            <h3><el-icon aria-hidden="true"><Check/></el-icon>项目已选好。</h3>
            <p>已选择「{{ selection.snapshot.name }}」，可以用这个项目开始模拟面试。</p>
          </div>
          <div class="selection-actions">
            <el-button :tag="RouterLink" type="primary" :to="{ path: '/formal', query: { projectId: current.id } }">
              用该项目开始正式模拟 →
            </el-button>
            <el-button :icon="Download" @click="exportSnapshot">下载项目资料</el-button>
            <RouterLink class="training-next-link" to="/training">去普通训练 →</RouterLink>
          </div>
        </div>
      </section>
    </div>
    <el-empty v-else-if="!loading" description="还没有可选择的已确认项目">
      <router-link to="/resumes" class="inline-link">先上传并确认一份简历 →</router-link>
    </el-empty>
  </main>
</template>

<style scoped>
.selection-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--s-2);
}

.selection-actions .el-button {
  margin-left: 0;
}

.training-next-link {
  display: inline-flex;
  align-items: center;
  min-height: var(--s-10);
  padding-inline: var(--s-2);
  border-radius: var(--r-md);
  color: var(--green);
  font-size: var(--fs-ui);
}

.training-next-link:hover {
  background: var(--green-100);
}
</style>
