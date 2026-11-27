<script setup lang="ts">
import {computed,reactive,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import type {FormInstance,FormRules} from 'element-plus'
import {api,showError} from '../api'
import {useAuth} from '../stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuth(),form=ref<FormInstance>(),busy=ref(false)
const register=computed(()=>route.path==='/register')
const fields=reactive({account:'',password:'',nickname:'',confirmation:''})
const rules:FormRules={account:[{required:true,message:'请输入账号',trigger:'blur'},{pattern:/^[A-Za-z0-9_]{8,16}$/,message:'8–16 位英文、数字或下划线',trigger:'blur'}],password:[{required:true,message:'请输入密码',trigger:'blur'},{validator:(_r,v,done)=>{if(register.value&&(!/(?=.*[A-Za-z])(?=.*[0-9])/.test(v)||v.length<8||new TextEncoder().encode(v).length>72))done(new Error('至少 8 位，含英文和数字，最多 72 字节'));else done()},trigger:'blur'}],nickname:[{required:true,message:'请输入昵称',trigger:'blur'},{max:64,message:'昵称最多 64 字',trigger:'blur'}],confirmation:[{validator:(_r,v,done)=>v===fields.password?done():done(new Error('两次密码不一致')),trigger:'blur'}]}
async function submit(){if(!await form.value?.validate().catch(()=>false))return;busy.value=true;try{if(register.value){await api.register({account:fields.account,password:fields.password,nickname:fields.nickname});ElMessage.success('注册成功，请登录');await router.push('/login')}else{await auth.login(fields.account,fields.password);const next=route.query.redirect;await router.push(auth.user?.role==='ADMIN'?'/admin/users':typeof next==='string'&&next.startsWith('/')&&!next.startsWith('//')?next:'/resumes')}}catch(e){showError(e)}finally{busy.value=false;fields.password='';fields.confirmation=''}}
</script>
<template>
  <main class="auth-layout">
    <section class="auth-story">
      <div class="eyebrow">账户</div>
      <h1>你的经历，<br><span class="highlight">值得被认真看见。</span></h1>
      <p class="auth-subtitle">面向校招的 AI 模拟面试，帮你练习如何讲清真实的项目经历。</p>

      <div class="auth-preview" aria-hidden="true">
        <div class="auth-preview__question">
          <span class="auth-preview__speaker">AI 追问</span>
          <p>你提到把接口响应从 800ms 降到 120ms，具体是在哪一层做的缓存？</p>
        </div>
        <div class="auth-preview__answer">
          <span></span><span></span>
        </div>
        <div class="auth-preview__scores">
          <span style="--sample-score: 82%"></span>
          <span style="--sample-score: 91%"></span>
          <span style="--sample-score: 61%"></span>
          <span style="--sample-score: 79%"></span>
          <span style="--sample-score: 88%"></span>
        </div>
      </div>
    </section>

    <section class="auth-form surface">
      <h2>{{ register ? '从这里，开始准备。' : '欢迎回来。' }}</h2>
      <p class="auth-form__intro">{{ register ? '创建你的学生账号，开始整理简历与项目。' : '登录面序，继续整理你的简历与项目。' }}</p>

      <el-form ref="form" :model="fields" :rules="rules" :disabled="busy" label-position="top" size="large" @submit.prevent="submit">
        <el-form-item label="账号" prop="account">
          <el-input v-model="fields.account" autocomplete="username" placeholder="8–16 位英文、数字或下划线" maxlength="16" />
        </el-form-item>
        <el-form-item v-if="register" label="昵称" prop="nickname">
          <el-input v-model="fields.nickname" autocomplete="nickname" placeholder="我们该如何称呼你" maxlength="64" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="fields.password"
            type="password"
            show-password
            :autocomplete="register ? 'new-password' : 'current-password'"
            :placeholder="register ? '至少 8 位，包含英文和数字' : '输入你的密码'"
          />
        </el-form-item>
        <el-form-item v-if="register" label="确认密码" prop="confirmation">
          <el-input v-model="fields.confirmation" type="password" show-password autocomplete="new-password" placeholder="再次输入密码" />
        </el-form-item>
        <el-button type="primary" class="auth-submit" native-type="submit" :loading="busy">
          {{ register ? '创建账号' : '登录面序' }}
        </el-button>
      </el-form>

      <p class="auth-switch">
        {{ register ? '已经有账号？' : '第一次来到面序？' }}
        <router-link :to="register ? '/login' : '/register'">{{ register ? '去登录' : '创建学生账号' }}</router-link>
      </p>
      <p v-if="register" class="fine-print">管理员账户由平台统一创建。</p>
    </section>
  </main>
</template>
