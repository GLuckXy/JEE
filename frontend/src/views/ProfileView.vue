<script setup lang="ts">
import {computed,reactive,ref} from 'vue'
import {Check} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import type {FormInstance,FormRules} from 'element-plus'
import {useAuth} from '../stores/auth'
import {api,showError} from '../api'
const auth=useAuth(),busy=ref(false),form=ref<FormInstance>()
const fields=reactive({nickname:auth.user?.nickname||'',email:auth.user?.email||'',phone:auth.user?.phone||'',school:auth.user?.school||'',major:auth.user?.major||''})
const baseline=ref(JSON.stringify(fields))
const dirty=computed(()=>JSON.stringify(fields)!==baseline.value)
const rules:FormRules={nickname:[{required:true,whitespace:true,message:'请填写昵称',trigger:'blur'},{max:64,message:'最多64字',trigger:'blur'}],email:[{type:'email',message:'请输入有效邮箱',trigger:'blur'}],phone:[{pattern:/^[+0-9 ()-]{0,32}$/,message:'请输入有效电话（最多32位）',trigger:'blur'}]}
async function save(){if(!await form.value?.validate().catch(()=>false))return;busy.value=true;try{auth.user=await api.profile({nickname:fields.nickname.trim(),email:fields.email||null,phone:fields.phone||null,school:fields.school||null,major:fields.major||null});baseline.value=JSON.stringify(fields);ElMessage.success('个人资料已保存')}catch(e){showError(e)}finally{busy.value=false}}
</script>
<template>
  <main class="page-shell narrow">
    <header class="page-heading">
      <div class="eyebrow">账户</div>
      <h1>个人资料</h1>
      <p class="muted">查看账号信息，维护昵称、联系方式与学校专业。</p>
    </header>
    <section class="surface profile-surface">
      <div class="profile-summary">
        <span class="avatar large">{{auth.user?.nickname.slice(0,1)}}</span>
        <div>
          <h2>{{auth.user?.nickname}}</h2>
          <p class="muted">{{auth.user?.account}} · {{auth.user?.role==='ADMIN'?'管理员':'学生账户'}}</p>
        </div>
        <span class="status-pill confirmed">账号正常</span>
      </div>
      <el-form ref="form" :model="fields" :rules="rules" :disabled="busy" label-position="top" size="large" @submit.prevent="save">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="fields.nickname" maxlength="64"/>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="邮箱（选填）" prop="email">
            <el-input v-model="fields.email" type="email" maxlength="254"/>
          </el-form-item>
          <el-form-item label="电话（选填）" prop="phone">
            <el-input v-model="fields.phone" maxlength="32"/>
          </el-form-item>
          <el-form-item label="学校（选填）">
            <el-input v-model="fields.school" maxlength="128"/>
          </el-form-item>
          <el-form-item label="专业（选填）">
            <el-input v-model="fields.major" maxlength="128"/>
          </el-form-item>
        </div>
        <div class="form-footer">
          <p class="fine-print">{{dirty?'修改尚未保存，确认后点击右侧按钮。':'资料已同步。账号与角色由平台管理。'}}</p>
          <el-button type="primary" native-type="submit" :icon="Check" :disabled="!dirty||busy" :loading="busy">保存资料</el-button>
        </div>
      </el-form>
    </section>
  </main>
</template>
