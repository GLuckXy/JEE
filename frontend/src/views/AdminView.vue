<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {api,showError} from '../api'
import type {User} from '../types'
import {dateTime} from '../types'
const rows=ref<User[]>([]),page=ref(1),total=ref(0),loading=ref(false),busy=ref(false),error=ref(''),detail=ref<User>(),drawer=ref(false),editOpen=ref(false),nickname=ref(''),editingId=ref('')
async function load(){loading.value=true;error.value='';try{const result=await api.users(page.value,10);rows.value=result.records;total.value=result.total}catch(e){error.value=e instanceof Error?e.message:'读取失败';showError(e)}finally{loading.value=false}}
async function inspect(user:User){try{detail.value=await api.user(user.id);drawer.value=true}catch(e){showError(e)}}
function edit(user:User){editingId.value=user.id;nickname.value=user.nickname;editOpen.value=true}
async function save(){if(!nickname.value.trim()){ElMessage.warning('昵称不可为空');return}busy.value=true;try{await api.userNickname(editingId.value,nickname.value.trim());editOpen.value=false;ElMessage.success('昵称已更新');await load()}catch(e){showError(e)}finally{busy.value=false}}
async function status(user:User){const next=user.status==='ACTIVE'?'DISABLED':'ACTIVE';try{await ElMessageBox.confirm(next==='DISABLED'?`禁用 ${user.account} 后，该学生现有令牌也会失效。确定继续？`:`恢复 ${user.account} 的登录权限？`,'维护学生账号状态',{confirmButtonText:next==='DISABLED'?'确认禁用':'确认恢复',cancelButtonText:'取消',type:'warning'});busy.value=true;await api.userStatus(user.id,next);ElMessage.success('账号状态已更新');await load()}catch(e){if(e!=='cancel'&&e!=='close')showError(e)}finally{busy.value=false}}
onMounted(load)
</script>
<template>
  <main class="page-shell">
    <header class="page-heading heading-with-actions">
      <div>
        <div class="eyebrow">后台</div>
        <h1>让账户管理，清晰有界。</h1>
        <p class="muted">管理基础资料与学生账户状态。私有简历与项目不在管理范围内。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新列表</el-button>
    </header>
    <section class="surface">
      <el-alert title="所有账号维护都会记录操作人和时间。初版不提供角色变更或密码重置。" type="info" :closable="false" show-icon/>
      <el-result v-if="error" icon="warning" :title="error">
        <template #extra>
          <el-button @click="load">重试</el-button>
        </template>
      </el-result>
      <el-table v-else v-loading="loading" :data="rows" empty-text="暂无账户" style="width:100%" class="admin-table">
        <el-table-column prop="account" label="账号" min-width="160"/>
        <el-table-column prop="nickname" label="昵称" min-width="140"/>
        <el-table-column label="角色" width="100">
          <template #default="{row}">{{row.role==='ADMIN'?'管理员':'学生'}}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{row}">
            <span class="status-pill" :class="{confirmed:row.status==='ACTIVE'}">{{row.status==='ACTIVE'?'正常':'已禁用'}}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170" class-name="num">
          <template #default="{row}">{{dateTime(row.createdAt)}}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="210" fixed="right">
          <template #default="{row}">
            <el-button link :disabled="busy" @click="inspect(row)">详情</el-button>
            <el-button link :disabled="busy" @click="edit(row)">修改昵称</el-button>
            <el-button v-if="row.role==='STUDENT'" link :type="row.status==='ACTIVE'?'danger':'primary'" :disabled="busy" @click="status(row)">{{row.status==='ACTIVE'?'禁用':'恢复'}}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p v-if="!error" class="fine-print table-scroll-hint">左右滑动表格查看完整资料与账号操作。</p>
      <el-pagination v-if="total>10" v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next" @current-change="load"/>
    </section>
    <el-dialog v-model="editOpen" title="修改账号昵称" width="440px" :close-on-click-modal="!busy">
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="昵称">
          <el-input v-model="nickname" maxlength="64"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="busy" @click="editOpen=false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="save">保存昵称</el-button>
      </template>
    </el-dialog>
    <el-drawer v-model="drawer" title="账号基础资料" size="min(480px,100vw)">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item v-for="(label,key) in {account:'账号',nickname:'昵称',email:'邮箱',phone:'电话',school:'学校',major:'专业',role:'角色',status:'状态'}" :key="key" :label="label">{{detail[key]||'未填写'}}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{dateTime(detail.createdAt)}}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </main>
</template>
