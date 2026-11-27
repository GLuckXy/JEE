<script setup lang="ts">
import {ref,computed,onMounted,onBeforeUnmount} from 'vue'
import {useRoute,useRouter,onBeforeRouteLeave} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {Download,Plus,Refresh,Clock,DocumentChecked,Right,Check} from '@element-plus/icons-vue'
import {api,ApiError,showError,downloadFile,TOKEN_KEY} from '../api'
import {dateTime,emptyProject,emptyExperience} from '../types'
import type {Detail,Payload,ResumeVersion} from '../types'
import PayloadReadOnly from '../components/PayloadReadOnly.vue'
import DeleteResumeButton from '../components/DeleteResumeButton.vue'
import {mergeParsed,changes,clonePayload} from '../mergeParsed'
const route=useRoute(),router=useRouter(),id=String(route.params.id)
const detail=ref<Detail>(),payload=ref<Payload>(),loading=ref(true),error=ref(''),busy=ref(false),baseline=ref(''),sourceTab=ref('text'),pdfUrl=ref(''),historyOpen=ref(false),historyBusy=ref(false),history=ref<ResumeVersion[]>([]),activeVersion=ref<number>(),conflict=ref(false),downloadBusy=ref(false),analysisOpen=ref(false),mergeMode=ref<'fill'|'replace'>('fill'),deleted=ref(false)
const dirty=computed(()=>!!payload.value&&JSON.stringify(payload.value)!==baseline.value)
const candidate=computed(()=>detail.value?.rawParseResult?.extracted)
const mergePreview=computed(()=>payload.value&&candidate.value?mergeMode.value==='replace'?{payload:clonePayload(candidate.value),skipped:[]}:mergeParsed(payload.value,candidate.value):undefined)
const differences=computed(()=>payload.value&&mergePreview.value?changes(payload.value,mergePreview.value.payload):[])
async function applyAnalysis(){if(!mergePreview.value||!differences.value.length)return;try{await ElMessageBox.confirm(mergeMode.value==='replace'?'将用解析候选替换当前全部草稿字段，包括非空内容。请先核对下方差异；此操作不会修改历史版本。':'将填入差异表中的空白字段及可确定的新条目，不覆盖已有文字。','应用解析结果',{confirmButtonText:mergeMode.value==='replace'?'确认替换草稿':'确认填入空白',cancelButtonText:'再检查一下',type:mergeMode.value==='replace'?'warning':'info'});payload.value=clonePayload(mergePreview.value.payload);analysisOpen.value=false;ElMessage.success('已填入页面，请校对后保存草稿')}catch(e){if(e!=='cancel'&&e!=='close')showError(e)}}
async function afterDelete(){deleted.value=true;baseline.value=JSON.stringify(payload.value);await router.replace('/resumes')}
const selectedVersion=computed(()=>history.value.find(v=>v.version===activeVersion.value))
const projectLabels={goal:'项目目标',responsibility:'本人职责',difficulty:'遇到的困难',solution:'解决过程',result:'结果与效果',improvement:'后续改进'} as const
function adopt(value:Detail){detail.value=value;payload.value=structuredClone(value.draft);baseline.value=JSON.stringify(payload.value);conflict.value=false}
async function load(){loading.value=true;error.value='';try{adopt(await api.detail(id))}catch(e){error.value=e instanceof Error?e.message:'加载失败'}finally{loading.value=false}}
function handle(e:unknown){if(e instanceof ApiError&&e.code==='VERSION_CONFLICT')conflict.value=true;showError(e)}
async function save(){if(!detail.value||!payload.value)return false;busy.value=true;try{adopt(await api.draft(id,detail.value.summary.revision,payload.value));ElMessage.success('草稿已保存，确认后才可用于面试');return true}catch(e){handle(e);return false}finally{busy.value=false}}
async function confirm(){if(!detail.value||!payload.value)return;const invalid=payload.value.projects.findIndex(p=>!p.name.trim()||!p.responsibility.trim());if(!payload.value.projects.length||invalid!==-1){ElMessage.warning(!payload.value.projects.length?'请至少添加一个项目':`项目 ${invalid+1} 的名称和本人职责必填`);return}try{await ElMessageBox.confirm('请核对所有经历。确认后将保存一个独立版本，供后续项目选择使用。','确认这份简历',{confirmButtonText:'确认并保存版本',cancelButtonText:'再检查一下'});if(dirty.value&&!await save())return;busy.value=true;adopt(await api.confirm(id,detail.value.summary.revision));ElMessage.success('简历已确认，可以选择项目了')}catch(e){if(e!=='cancel'&&e!=='close')handle(e)}finally{busy.value=false}}
async function retry(){if(dirty.value){ElMessage.warning('请先保存草稿，避免丢失修改');return}busy.value=true;try{adopt(await api.retry(id));ElMessage[detail.value?.summary.parseStatus==='SUCCESS'?'success':'warning'](detail.value?.summary.parseStatus==='SUCCESS'?'已重新提取文本':'未能提取文字，可继续手动补充')}catch(e){handle(e)}finally{busy.value=false}}
async function preview(){if(pdfUrl.value)return;busy.value=true;try{const blob=await api.file(detail.value!.summary.fileId);pdfUrl.value=URL.createObjectURL(blob)}catch(e){showError(e);sourceTab.value='text'}finally{busy.value=false}}
async function download(){downloadBusy.value=true;try{await downloadFile(detail.value!.summary.fileId,detail.value!.summary.originalName)}catch(e){showError(e)}finally{downloadBusy.value=false}}
async function versions(){historyOpen.value=true;historyBusy.value=true;try{history.value=await api.versions(id);activeVersion.value=history.value[0]?.version}catch(e){showError(e)}finally{historyBusy.value=false}}
async function reload(){try{if(dirty.value)await ElMessageBox.confirm('重新加载会放弃页面上未保存的修改。','重新加载',{confirmButtonText:'放弃修改并加载',cancelButtonText:'保留修改'});await load()}catch(e){if(e!=='cancel'&&e!=='close')showError(e)}}
async function removeDraftItem(section:'projects'|'education'|'internships',index:number){try{await ElMessageBox.confirm('将此条目从当前草稿中移除？保存并重新确认前，不影响历史版本。','移除草稿条目',{confirmButtonText:'移除',cancelButtonText:'保留'});payload.value?.[section].splice(index,1)}catch{/* Cancel keeps draft intact. */}}
function jumpTo(section:string){document.getElementById(`edit-${section}`)?.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'start'})}
function beforeUnload(event:BeforeUnloadEvent){if(dirty.value||busy.value){event.preventDefault();event.returnValue=''}}
onBeforeRouteLeave(async()=>{if(deleted.value||!sessionStorage.getItem(TOKEN_KEY))return true;if(busy.value){ElMessage.warning('操作进行中，请稍候');return false}if(!dirty.value)return true;try{await ElMessageBox.confirm('你有尚未保存的修改，离开后将不会保留。','离开校对页',{confirmButtonText:'放弃修改并离开',cancelButtonText:'继续编辑'});return true}catch{return false}})
onMounted(()=>{void load();window.addEventListener('beforeunload',beforeUnload)})
onBeforeUnmount(()=>{window.removeEventListener('beforeunload',beforeUnload);if(pdfUrl.value)URL.revokeObjectURL(pdfUrl.value)})
</script>
<template>
  <main class="page-shell editor-shell">
    <router-link class="back-link" to="/resumes">← 返回我的简历</router-link>
    <el-skeleton v-if="loading" :rows="10" animated/>
    <el-result v-else-if="error" icon="warning" title="无法打开这份简历" :sub-title="error">
      <template #extra>
        <el-button @click="load">重试</el-button>
      </template>
    </el-result>
    <template v-else-if="detail&&payload">
      <div class="page-heading heading-with-actions">
        <div>
          <div class="eyebrow">准备</div>
          <h1>把每一段经历，写准确。</h1>
          <p class="muted">{{detail.summary.originalName}} · {{detail.summary.version?`最近确认 V${detail.summary.version}`:'尚未确认'}}</p>
        </div>
        <div class="heading-actions">
          <el-button :icon="Download" :disabled="!detail.allowedActions.includes('DOWNLOAD_FILE')" :loading="downloadBusy" @click="download">原文件</el-button>
          <el-button :icon="Clock" @click="versions">版本记录</el-button>
          <DeleteResumeButton v-if="detail.allowedActions.includes('DELETE')" :resume="detail.summary" :disabled="busy" @deleted="afterDelete"/>
        </div>
      </div>
      <el-alert v-if="conflict" type="warning" title="资料已在别处更新。当前编辑内容仍在，请先复制需要保留的文字，再重新加载最新版本。" :closable="false" show-icon>
        <el-button text @click="reload">重新加载最新版本</el-button>
      </el-alert>
      <el-alert v-if="detail.summary.parseStatus==='FAILED'" type="warning" :title="detail.summary.failureReason||'未能解析文字，可手动补充'" :closable="false" show-icon/>
      <el-alert v-if="detail.summary.confirmStatus==='CONFIRMED'" type="success" title="当前简历已确认。再次修改并保存后，会回到待确认状态，历史确认版本仍保留。" :closable="false" show-icon/>
      <ol class="editor-progress" aria-label="简历准备进度">
        <li class="is-done">
          <b>
            <el-icon>
              <Check/>
            </el-icon>
          </b>
          <span>上传简历</span>
        </li>
        <li :class="detail.summary.confirmStatus==='CONFIRMED'&&!dirty?'is-done':'is-current'" :aria-current="detail.summary.confirmStatus!=='CONFIRMED'||dirty?'step':undefined">
          <b class="num">2</b>
          <span>校对并确认</span>
        </li>
        <li :class="{'is-current':detail.summary.confirmStatus==='CONFIRMED'&&!dirty}" :aria-current="detail.summary.confirmStatus==='CONFIRMED'&&!dirty?'step':undefined">
          <b class="num">3</b>
          <span>选择项目</span>
        </li>
      </ol>
      <div class="editor-grid">
        <section class="source-panel surface">
          <div class="panel-heading">
            <h2>原始资料</h2>
            <span class="meta">仅供校对参考</span>
          </div>
          <el-button v-if="candidate" class="analysis-open" :icon="DocumentChecked" :disabled="busy" @click="analysisOpen=true">查看解析候选与填表建议</el-button>
          <p v-if="candidate" class="fine-print source-hint">文本提取完成 ≠ 信息完整，请核对来源后确认。</p>
          <el-tabs v-model="sourceTab" @tab-change="(value: string | number)=>value==='pdf'&&preview()">
            <el-tab-pane label="提取文本" name="text">
              <pre class="source-text">{{detail.rawParseResult?.text||'未提取到文字。你可以参照原文件，在右侧手动填写真实经历。'}}</pre>
            </el-tab-pane>
            <el-tab-pane v-if="detail.summary.originalName.toLowerCase().endsWith('.pdf')&&detail.allowedActions.includes('DOWNLOAD_FILE')" label="PDF 原文件" name="pdf">
              <div v-if="pdfUrl" class="source-pdf">
                <iframe :src="pdfUrl" title="本人简历原文件预览" class="pdf-preview"/>
              </div>
              <p v-else class="muted">正在读取授权文件…</p>
            </el-tab-pane>
          </el-tabs>
          <el-button v-if="detail.allowedActions.includes('RETRY_PARSE')" :icon="Refresh" :loading="busy" @click="retry">重新解析</el-button>
        </section>
        <section class="edit-panel surface">
          <div class="panel-heading">
            <h2>校对与补充</h2>
          </div>
          <p class="fine-print editor-intro">可选经历允许留空。项目名称与本人职责为确认必填项。</p>
          <nav class="editor-jumps" aria-label="跳转到编辑内容">
            <el-button v-for="item in [{id:'skills',label:'专业技能'},{id:'education',label:'教育经历'},{id:'internships',label:'实习经历'},{id:'projects',label:'项目经历'}]" :key="item.id" text @click="jumpTo(item.id)">{{item.label}}</el-button>
          </nav>
          <el-form label-position="top" :disabled="busy" size="large">
            <section id="edit-skills" class="form-section">
              <h3 class="editor-section-title"><span class="editor-section-number num" aria-hidden="true">01</span>专业技能</h3>
              <el-select v-model="payload.skills" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="输入技能并按回车添加，最多100项" :multiple-limit="100" class="full-width">
                <el-option v-for="skill in payload.skills" :key="skill" :label="skill" :value="skill"/>
              </el-select>
            </section>
            <section v-for="(section,index) in [{key:'education' as const,label:'教育经历'},{key:'internships' as const,label:'实习经历'}]" :key="section.key" :id="`edit-${section.key}`" class="form-section">
              <div class="section-head small-head">
                <h3 class="editor-section-title"><span class="editor-section-number num" aria-hidden="true">0{{index+2}}</span>{{section.label}} <span class="muted optional">选填</span></h3>
                <el-button text :icon="Plus" :disabled="payload[section.key].length>=30" @click="payload[section.key].push(emptyExperience())">添加</el-button>
              </div>
              <p v-if="!payload[section.key].length" class="fine-print">暂无内容，有相关经历时再补充。</p>
              <div v-for="(item,index) in payload[section.key]" :key="index" class="draft-item surface-sunken">
                <div class="item-title">
                  <span>{{section.label}} {{index+1}}</span>
                  <el-button text type="danger" size="small" @click="removeDraftItem(section.key,index)">移除</el-button>
                </div>
                <div class="form-grid">
                  <el-form-item :label="section.key==='education'?'学校':'实习单位'">
                    <el-input v-model="item.organization" maxlength="256"/>
                  </el-form-item>
                  <el-form-item :label="section.key==='education'?'专业 / 学位':'岗位'">
                    <el-input v-model="item.title" maxlength="256"/>
                  </el-form-item>
                </div>
                <el-form-item label="起止时间">
                  <el-input v-model="item.period" placeholder="例如 2023.09 – 2027.06" maxlength="128"/>
                </el-form-item>
                <el-form-item label="经历说明">
                  <el-input v-model="item.description" type="textarea" :rows="3" maxlength="5000" show-word-limit/>
                </el-form-item>
              </div>
            </section>
            <section id="edit-projects" class="form-section">
              <div class="section-head small-head">
                <h3 class="editor-section-title"><span class="editor-section-number num" aria-hidden="true">04</span>项目经历 <span class="required-note">确认时至少一项</span></h3>
                <el-button text :icon="Plus" :disabled="payload.projects.length>=30" @click="payload.projects.push(emptyProject())">添加项目</el-button>
              </div>
              <el-empty v-if="!payload.projects.length" description="添加一个你真实参与的项目" :image-size="65">
                <el-button @click="payload.projects.push(emptyProject())">添加项目</el-button>
              </el-empty>
              <div v-for="(project,index) in payload.projects" :key="index" class="draft-item surface-sunken project-item">
                <div class="item-title">
                  <span>PROJECT {{String(index+1).padStart(2,'0')}}</span>
                  <el-button text type="danger" size="small" @click="removeDraftItem('projects',index)">移除</el-button>
                </div>
                <el-form-item label="项目名称 *">
                  <el-input v-model="project.name" maxlength="256" placeholder="用准确的名字描述你的项目"/>
                </el-form-item>
                <el-form-item v-for="(label,key) in projectLabels" :key="key" :label="label+(key==='responsibility'?' *':'')">
                  <el-input v-model="project[key]" type="textarea" :rows="key==='responsibility'?3:2" maxlength="5000" show-word-limit :placeholder="key==='responsibility'?'写清你本人负责的部分，不要代填团队其他人的工作':'按真实经历填写，没有可留空'"/>
                </el-form-item>
                <el-form-item label="技术栈">
                  <el-select v-model="project.techStack" multiple filterable allow-create default-first-option :reserve-keyword="false" :multiple-limit="100" placeholder="输入技术并回车添加" class="full-width">
                    <el-option v-for="tech in project.techStack" :key="tech" :value="tech" :label="tech"/>
                  </el-select>
                </el-form-item>
              </div>
            </section>
          </el-form>
        </section>
      </div>
      <div class="editor-actions">
        <div class="editor-save-status num" :class="{ 'is-dirty': dirty }" role="status">
          <span v-if="dirty">修改尚未保存</span>
          <span v-else-if="detail.summary.confirmStatus==='CONFIRMED'">
      当前版本已确认<span v-if="detail.summary.confirmedAt"> · {{ dateTime(detail.summary.confirmedAt).slice(-5) }}</span>
    </span>
          <span v-else>草稿已保存</span>
        </div>
        <div class="action-buttons">
          <el-button :disabled="busy||!dirty" :loading="busy" @click="save">保存草稿</el-button>
          <el-button v-if="detail.summary.confirmStatus!=='CONFIRMED'||dirty" type="primary" :loading="busy" :disabled="conflict" @click="confirm">确认简历<el-icon aria-hidden="true"><Right/></el-icon></el-button>
          <el-button v-else type="primary" @click="router.push({path:'/projects',query:{resumeId:id}})">选择项目<el-icon aria-hidden="true"><Right/></el-icon></el-button>
        </div>
      </div>
      <el-drawer v-model="analysisOpen" title="解析候选与填表建议" size="min(820px, 100vw)">
        <template v-if="candidate">
          <p class="muted">先查看候选与原文。应用后只改变当前页面草稿，保存后才写入数据库。</p>
          <p v-for="warning in detail.rawParseResult?.warnings||[]" :key="warning" class="parse-warning">{{warning}}</p>
          <el-radio-group v-model="mergeMode" aria-label="填表方式">
            <el-radio-button value="fill">仅填入空白字段</el-radio-button>
            <el-radio-button value="replace">替换全部草稿</el-radio-button>
          </el-radio-group>
          <p class="fine-print">按学校/公司与时间、项目名称匹配，无法确定匹配的条目不按顺序覆盖。</p>
          <p v-for="skip in mergePreview?.skipped||[]" :key="skip" class="parse-warning">{{skip}}</p>
          <el-table :data="differences" max-height="320" empty-text="没有可应用的变化">
            <el-table-column prop="field" label="字段" min-width="140"/>
            <el-table-column prop="before" label="当前内容" min-width="140">
              <template #default="{row}">
                <span class="preserve">{{row.before||'（空）'}}</span>
              </template>
            </el-table-column>
            <el-table-column prop="after" label="应用后" min-width="140">
              <template #default="{row}">
                <span class="preserve">{{row.after||'（空）'}}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" class="analysis-apply" :disabled="!differences.length||busy" @click="applyAnalysis">{{mergeMode==='fill'?'填入空白字段':'应用替换结果'}}</el-button>
          <el-collapse>
            <el-collapse-item title="查看完整解析候选" name="candidate">
              <PayloadReadOnly :payload="candidate"/>
            </el-collapse-item>
            <el-collapse-item title="字段来源原文（未修改解析候选的依据）" name="evidence">
              <div v-for="(proof,index) in detail.rawParseResult?.evidence||[]" :key="index" class="proof-item">
                <strong>{{proof.field}}</strong>
                <span>{{proof.confidence==='MATCHED'?'标签/格式匹配，仍需核对':'候选识别，建议核对'}}</span>
                <p class="preserve">{{proof.source}}</p>
              </div>
              <p v-if="!detail.rawParseResult?.evidence?.length" class="muted">旧版解析未保存来源，请先保存草稿后重新解析。</p>
            </el-collapse-item>
          </el-collapse>
        </template>
      </el-drawer>
      <el-drawer v-model="historyOpen" title="确认版本记录" size="min(680px, 100vw)">
        <el-skeleton v-if="historyBusy" :rows="8" animated/>
        <el-empty v-else-if="!history.length" description="尚未生成确认版本"/>
        <template v-else>
          <el-select v-model="activeVersion" class="full-width" aria-label="选择确认版本">
            <el-option v-for="version in history" :key="version.id" :value="version.version" :label="`V${version.version} · ${dateTime(version.confirmedAt)}`"/>
          </el-select>
          <p class="fine-print">历史确认内容仅供查看，不会自动替换当前草稿。</p>
          <PayloadReadOnly v-if="selectedVersion" :payload="selectedVersion.payload"/>
        </template>
      </el-drawer>
    </template>
  </main>
</template>
