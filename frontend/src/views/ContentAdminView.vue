<script setup>
import {computed, onMounted, ref, watch} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {moduleRequest, showError} from '../api'

const labels={jobs:'岗位',categories:'分类',questions:'题库',demos:'典型示范',rules:'评价规则'}
const difficulty={EASY:'基础',MEDIUM:'进阶',HARD:'综合'}
const modes={ALL:'全部训练',NORMAL:'普通训练',SPECIAL:'专项训练',FORMAL:'正式模拟'}
const weightLabels={completeness:'完整性',accuracy:'准确性',structure:'结构',relevance:'相关性',expression:'表达'}
const defaults={completeness:30,accuracy:30,structure:15,relevance:15,expression:10}
const fields={
  jobs:[['name','岗位名称'],['description','岗位简介',2000],['requirements','岗位要求',4000],['interviewFocus','面试重点',4000]],
  categories:[['name','分类名称'],['description','分类说明',1000]],
  questions:[['title','题目标题'],['content','题干',6000],['referencePoints','参考要点（每行一条）',10000],['sourceNote','来源说明',500]],
  demos:[['title','示范标题'],['introduction','示范简介',2000],['sourceNote','来源说明',500]],
  rules:[['name','规则名称']],
}
const stageFields=[['title','阶段标题',80],['interviewerQuestion','面试官提问',3000],['sampleAnswer','示范回答',6000],['followUp','追问（可选）',3000],['explanation','解读',4000]]
const tab=ref('jobs'), page=ref(1), total=ref(0), entries=ref([]), jobs=ref([]), categories=ref([])
const busy=ref(false), saving=ref(false), toggling=ref(null), edit=ref(false), itemId=ref(null), form=ref({}), error=ref('')
const importing=ref(false), importResult=ref('')
const kindPath=(kind)=>kind==='rules'?'/admin/evaluation-rules':`/admin/content/${kind}`
const forJob=computed(()=>categories.value.filter(category=>category.jobId===form.value.jobId))
const blankStage=()=>Object.fromEntries(stageFields.map(([key])=>[key,'']))
let revision=0

async function load(){
  const active=tab.value, current=++revision
  busy.value=true;error.value=''
  try{
    const result=await moduleRequest('GET',kindPath(active),undefined,{params:active==='rules'?undefined:{page:page.value,size:12}})
    if(current===revision){entries.value=result.records??result;total.value=result.total??result.length}
  }catch(failure){if(current===revision)error.value=failure.message}
  finally{if(current===revision)busy.value=false}
}

async function collect(kind){
  const values=[]
  for(let next=1;;next++){
    const result=await moduleRequest('GET',kindPath(kind),undefined,{params:{page:next,size:100}})
    values.push(...result.records)
    if(!result.records.length||values.length>=result.total)return values
  }
}

async function open(item){
  error.value=''
  const active=tab.value
  busy.value=true
  try{
    if(['categories','questions','demos'].includes(active))jobs.value=await collect('jobs')
    if(active==='questions')categories.value=await collect('categories')
    const original=item?await moduleRequest('GET',`${kindPath(active)}/${item.id}`):{}
    if(active!==tab.value)return
    itemId.value=item?.id??null
    const draft=Object.fromEntries(fields[active].map(([key])=>[key,original[key]??'']))
    if(['categories','questions','demos'].includes(active))draft.jobId=original.jobId??jobs.value[0]?.id
    if(['jobs','categories','questions','rules'].includes(active))draft.enabled=original.enabled??true
    if(['jobs','categories'].includes(active))draft.sortOrder=original.sortOrder??0
    if(active==='questions'){draft.categoryId=original.categoryId;draft.difficulty=original.difficulty??'MEDIUM'}
    if(active==='demos')draft.stages=original.stages?.map(stage=>Object.fromEntries(stageFields.map(([key])=>[key,stage[key]??''])))??[blankStage()]
    if(active==='rules'){draft.modeScope=original.modeScope??'ALL';draft.published=original.published??false;draft.weights={...defaults,...original.weights}}
    form.value=draft;edit.value=true
  }catch(failure){showError(failure)}finally{busy.value=false}
}

async function save(){
  saving.value=true
  try{
    const data={...form.value}
    if(tab.value==='demos')data.stages=data.stages.map((stage,index)=>({...stage,sortOrder:index+1}))
    await moduleRequest(itemId.value?'PUT':'POST',kindPath(tab.value)+(itemId.value?`/${itemId.value}`:''),data)
    edit.value=false;ElMessage.success('已保存');await load()
  }catch(failure){showError(failure)}finally{saving.value=false}
}

async function toggle(item,publish=false){
  toggling.value=item.id
  const path=`${kindPath(tab.value)}/${item.id}`
  try{
    if(tab.value==='demos')await moduleRequest('PATCH',`${path}/status`,{status:item.status==='PUBLISHED'?'DRAFT':'PUBLISHED'})
    else if(tab.value==='rules')await moduleRequest('PUT',`${path}/status`,undefined,{params:{enabled:publish?item.enabled:!item.enabled,published:publish?!item.published:item.published}})
    else await moduleRequest('PATCH',`${path}/enabled`,{enabled:!item.enabled})
    await load()
  }catch(failure){showError(failure)}finally{toggling.value=null}
}

async function installTeachingContent(){
  if(importing.value)return
  try{
    await ElMessageBox.confirm('导入后，测试岗位和题目立即对学生可见，典型示范仍需审核发布。同名岗位会跳过，不覆盖已有内容。','导入测试题库',{confirmButtonText:'确认导入',cancelButtonText:'取消',type:'warning'})
  }catch{return}
  importing.value=true;importResult.value=''
  try{
    const result=await moduleRequest('POST','/admin/content/teaching-content')
    importResult.value=result.jobs
      ? `已导入 ${result.jobs} 个岗位、${result.categories} 个分类和 ${result.questions} 道题；${result.drafts} 份示范待发布。`
      : '测试岗位已存在，没有修改已有内容。'
    if(result.jobs&&result.skippedJobs)importResult.value+=` 跳过 ${result.skippedJobs} 个同名岗位。`
    if(result.jobs)await load()
  }catch(failure){showError(failure)}finally{importing.value=false}
}

watch(tab,()=>{entries.value=[];total.value=0;page.value=1;void load()})
onMounted(load)
</script>

<template>
  <main class="page-shell">
    <header class="page-heading"><div class="eyebrow">CONTENT WORKSPACE</div><h1>内容与评价规则</h1><p class="muted">管理所有训练使用的公共内容。修改题库不会重写学生的历史报告。</p></header>
    <section class="surface admin-content" v-loading="busy">
      <el-tabs v-model="tab" aria-label="管理类别"><el-tab-pane v-for="[key,label] in Object.entries(labels)" :key="key" :label="label" :name="key"/></el-tabs>
      <aside v-if="tab==='jobs'" class="teaching-kit" aria-label="初次配置">
        <div><span class="eyebrow">初次配置</span><h2>用测试题库跑通训练</h2><p>包含 Java 后端、Web 前端两套虚构教学内容。导入的岗位带「测试」标记，示范默认保持草稿。</p></div>
        <el-button plain :loading="importing" @click="installTeachingContent">导入测试题库</el-button>
        <p v-if="importResult" class="teaching-result" role="status">{{importResult}}</p>
      </aside>
      <div class="admin-row"><h2>{{labels[tab]}}</h2><el-button type="primary" @click="open()">新建{{tab==='questions'?'题目':labels[tab]}}</el-button></div>
      <el-alert v-if="error" type="error" :title="error" :closable="false"><el-button text @click="load">重新读取</el-button></el-alert>
      <el-empty v-else-if="!busy&&!entries.length" description="还没有记录"/>
      <article v-for="item in entries" :key="item.id" class="admin-row admin-item">
        <div><strong>{{item.name||item.title}}</strong><p class="fine-print">{{item.modeScope?modes[item.modeScope]:''}} {{item.difficulty?difficulty[item.difficulty]:''}} {{item.status?item.status==='PUBLISHED'?'已发布':'草稿':item.enabled?'已启用':'已停用'}} {{tab==='rules'?(item.published?'· 已发布':'· 草稿'):''}}</p></div>
        <div class="admin-actions"><el-button size="small" @click="open(item)">编辑</el-button><el-button size="small" :loading="toggling===item.id" @click="toggle(item)">{{tab==='demos'?(item.status==='PUBLISHED'?'撤回':'发布'):(item.enabled?'停用':'启用')}}</el-button><el-button v-if="tab==='rules'" size="small" :disabled="toggling===item.id" @click="toggle(item,true)">{{item.published?'撤回':'发布'}}</el-button></div>
      </article>
      <el-pagination v-if="tab!=='rules'&&total>12" v-model:current-page="page" :page-size="12" :total="total" layout="prev,pager,next" @current-change="load"/>
    </section>
    <el-dialog v-model="edit" :title="`${itemId?'编辑':'新建'}${tab==='questions'?'题目':labels[tab]}`" width="660px" :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item v-if="['categories','questions','demos'].includes(tab)" label="所属岗位"><el-select v-model="form.jobId" @change="form.categoryId=undefined"><el-option v-for="job in jobs" :key="job.id" :value="job.id" :label="job.name"/></el-select></el-form-item>
        <el-form-item v-if="tab==='questions'" label="所属分类"><el-select v-model="form.categoryId"><el-option v-for="category in forJob" :key="category.id" :value="category.id" :label="category.name"/></el-select></el-form-item>
        <el-form-item v-if="tab==='questions'" label="难度"><el-select v-model="form.difficulty"><el-option v-for="[key,label] in Object.entries(difficulty)" :key="key" :value="key" :label="label"/></el-select></el-form-item>
        <el-form-item v-for="[key,label,limit] in fields[tab]" :key="key" :label="label"><el-input v-model="form[key]" :type="limit?'textarea':'text'" :rows="3" :maxlength="limit||(key==='title'?tab==='demos'?150:200:80)"/></el-form-item>
        <el-form-item v-if="['jobs','categories'].includes(tab)" label="显示顺序"><el-input-number v-model="form.sortOrder" :min="0" :max="9999" :precision="0"/></el-form-item>
        <el-form-item v-if="tab!=='demos'" label="启用"><el-switch v-model="form.enabled"/></el-form-item>
        <template v-if="tab==='demos'">
          <section v-for="(stage,index) in form.stages" :key="index" class="admin-stage"><div class="admin-row"><h3>阶段 {{index+1}}</h3><el-button text @click="form.stages.splice(index,1)">移除此阶段</el-button></div><el-form-item v-for="[key,label,limit] in stageFields" :key="key" :label="label"><el-input v-model="stage[key]" :type="key==='title'?'text':'textarea'" :rows="2" :maxlength="limit"/></el-form-item></section>
          <el-button :disabled="form.stages.length>=50" @click="form.stages.push(blankStage())">添加阶段</el-button>
        </template>
        <template v-if="tab==='rules'">
          <el-form-item label="适用模式"><el-select v-model="form.modeScope"><el-option v-for="[key,label] in Object.entries(modes)" :key="key" :value="key" :label="label"/></el-select></el-form-item>
          <el-form-item label="发布"><el-switch v-model="form.published"/></el-form-item>
          <div class="weight-grid"><el-form-item v-for="[key,label] in Object.entries(weightLabels)" :key="key" :label="label"><el-input-number v-model="form.weights[key]" :min="0" :max="100" :precision="0"/></el-form-item></div>
        </template>
        <div class="admin-actions"><el-button type="primary" native-type="submit" :loading="saving">保存</el-button><el-button :disabled="saving" @click="edit=false">取消</el-button></div>
      </el-form>
    </el-dialog>
  </main>
</template>

<style scoped>
.admin-row,.admin-actions{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.admin-row{justify-content:space-between}.admin-actions .el-button+.el-button{margin-left:0}.admin-item{border-top:1px solid var(--line);padding:14px 0}.admin-item p{margin:5px 0 0}.admin-stage{background:#f4f8f5;padding:14px;margin-bottom:14px;border-radius:10px}.weight-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(125px,1fr));gap:8px}.weight-grid .el-input-number{width:120px}
.teaching-kit{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:12px 24px;border-left:3px solid var(--green,#157865);background:#f1f7f3;padding:16px 20px;margin:0 0 18px}.teaching-kit h2{font-size:1.1rem;margin:3px 0 4px}.teaching-kit p{color:var(--muted);margin:0}.teaching-kit .teaching-result{grid-column:1/-1;color:var(--green,#157865)}@media(max-width:620px){.teaching-kit{grid-template-columns:minmax(0,1fr);justify-items:start}}
</style>
