<script setup lang="ts">
import {onMounted,ref,computed} from 'vue'
import {Upload,Right} from '@element-plus/icons-vue'
import {api,showError} from '../api'
import {dateTime} from '../types'
import type {Summary} from '../types'
import UploadDialog from '../components/UploadDialog.vue'
import DeleteResumeButton from '../components/DeleteResumeButton.vue'
import PrepRail from '../components/PrepRail.vue'
import SegmentedControl from '../components/SegmentedControl.vue'

const resumeFilters = [
  { value: 'all', label: '全部' },
  { value: 'DRAFT', label: '待校对' },
  { value: 'CONFIRMED', label: '已确认' },
]
const loading=ref(true),error=ref(''),records=ref<Summary[]>([]),total=ref(0),page=ref(1),filter=ref('all'),uploadOpen=ref(false)
const visible=computed(()=>records.value.filter(r=>filter.value==='all'||r.confirmStatus===filter.value))
const uploadedDates = computed(() => Object.fromEntries(records.value.map(({ id, createdAt }) => [
  id,
  dateTime(createdAt).replace(/^\d{4}\/(\d{1,2})\/(\d{1,2})/, (_, month, day) => Number(month) + '月' + Number(day) + '日'),
])))
async function load(){loading.value=true;error.value='';try{const result=await api.resumes(page.value,8);records.value=result.records;total.value=result.total}catch(e){error.value=e instanceof Error?e.message:'加载失败';showError(e)}finally{loading.value=false}}
async function afterDelete(){if(records.value.length===1&&page.value>1)page.value--;await load()}
onMounted(load)
</script>
<template>
  <main class="page-shell resume-workspace">
    <header class="resume-heading">
      <div>
        <div class="resume-heading__title">
          <h1>我的简历</h1>
          <span class="resume-count num">{{ total }}<span class="visually-hidden"> 份简历</span></span>
        </div>
        <p class="muted">PDF / Word，最大 10 MB</p>
      </div>
      <el-button type="primary" :icon="Upload" @click="uploadOpen=true">上传简历</el-button>
    </header>

    <PrepRail v-if="!loading && !error" :records="records" />

    <section class="resume-library" aria-labelledby="resume-library-title">
      <div class="resume-library__heading">
        <h2 id="resume-library-title">简历列表</h2>
        <SegmentedControl v-model="filter" :options="resumeFilters" label="当前页状态筛选" />
      </div>
      <p v-if="total > 8" class="fine-print">状态筛选作用于当前页，全部 {{ total }} 份简历可通过分页查看。</p>

      <el-skeleton v-if="loading" :rows="5" animated class="loading-block" />
      <el-result v-else-if="error" icon="warning" title="暂时无法加载简历" :sub-title="error">
        <template #extra><el-button @click="load">重新加载</el-button></template>
      </el-result>
      <div v-else-if="visible.length" class="resume-grid">
        <article v-for="resume in visible" :key="resume.id" class="resume-card">
          <div class="card-top">
            <span class="resume-file-type">{{ resume.originalName.match(/\.(pdf|docx?)$/i)?.[1]?.toUpperCase() || '文件' }}</span>
            <span class="status-pill" :class="{ confirmed: resume.confirmStatus === 'CONFIRMED', failed: resume.parseStatus === 'FAILED' && resume.confirmStatus !== 'CONFIRMED' }">
              {{ resume.confirmStatus === 'CONFIRMED' ? '已确认' : resume.parseStatus === 'FAILED' ? '待手动补充' : '待校对' }}
            </span>
          </div>
          <h3 :title="resume.originalName">{{ resume.originalName.replace(/\.[^.]+$/, '') }}</h3>
          <p class="resume-card__meta num">
            上传于 {{ uploadedDates[resume.id] }}<template v-if="resume.version > 0"> · V{{ resume.version }}</template>
          </p>
          <p class="card-hint">
            {{ resume.confirmStatus === 'CONFIRMED'
              ? '可以选择已确认的项目继续准备。'
              : resume.parseStatus === 'SUCCESS'
                ? '已提取文字，请核对项目名称与本人职责。'
                : resume.parseStatus === 'FAILED'
                  ? '原文件已保留，可以参照原文手动填写。'
                  : '文件正在处理中，请稍后刷新查看。' }}
          </p>
          <div class="card-actions">
            <router-link v-if="resume.confirmStatus === 'CONFIRMED'" class="card-primary" :to="{ path: '/projects', query: { resumeId: resume.id } }">
              选择项目<el-icon aria-hidden="true"><Right /></el-icon>
            </router-link>
            <router-link v-else class="card-primary" :to="'/resumes/' + resume.id">
              继续校对<el-icon aria-hidden="true"><Right /></el-icon>
            </router-link>
            <router-link
              v-if="resume.confirmStatus === 'CONFIRMED'"
              class="card-secondary"
              :to="'/resumes/' + resume.id"
              aria-label="查看详情与版本"
              title="查看详情与版本"
            >
              查看详情
            </router-link>
            <DeleteResumeButton :resume="resume" compact @deleted="afterDelete" />
          </div>
        </article>
      </div>

      <el-empty v-else :description="records.length ? '本页暂无该状态的简历' : '还没有简历。从你的第一份经历开始。'">
        <el-button v-if="!records.length" @click="uploadOpen=true">上传第一份简历</el-button>
        <el-button v-else @click="filter='all'">查看全部</el-button>
      </el-empty>

      <div class="resume-library__footer">
        <el-button text :loading="loading" @click="load">刷新列表 ↻</el-button>
      </div>
      <el-pagination v-if="total > 8" v-model:current-page="page" :page-size="8" :total="total" layout="prev, pager, next" @current-change="load" />
    </section>
    <UploadDialog v-model="uploadOpen" />
  </main>
</template>
