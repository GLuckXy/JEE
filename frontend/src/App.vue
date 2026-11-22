<script setup lang="ts">
import {onMounted,onBeforeUnmount,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useAuth} from './stores/auth'
import {showError,TOKEN_KEY} from './api'
const auth=useAuth(),route=useRoute(),router=useRouter(),ready=ref(false),startupError=ref(false)
const studentLinks=[['/resumes','我的简历'],['/projects','项目资料'],['/demos','典型示范'],['/training','普通训练'],['/practice','专项训练'],['/formal','正式模拟'],['/reports','报告与历史']]
async function initialize(){startupError.value=false;try{await auth.restore();ready.value=true;if(auth.user&&route.meta.public)await router.replace(auth.user.role==='ADMIN'?'/admin/users':'/resumes')}catch(e){if(!sessionStorage.getItem(TOKEN_KEY)){auth.clear();ready.value=true;await router.replace('/login')}else{startupError.value=true;showError(e)}}}
function expired(){auth.clear();ElMessage.warning('登录已失效，请重新登录。');void router.replace('/login')}
async function logout(){try{await ElMessageBox.confirm('退出当前账户？未保存的内容将不会保留。','退出登录',{confirmButtonText:'退出',cancelButtonText:'取消'});await auth.logout();await router.replace('/login')}catch(e){if(e!=='cancel'&&e!=='close')showError(e)}}
onMounted(()=>{window.addEventListener('session-expired',expired);void initialize()})
onBeforeUnmount(()=>window.removeEventListener('session-expired',expired))
</script>
<template>
  <el-config-provider><div v-if="!ready" class="startup"><div class="brand">面序<span class="brand-mark"/></div><p>{{startupError?'暂时无法连接服务。':'正在准备你的工作台…'}}</p><el-button v-if="startupError" @click="initialize">重新连接</el-button></div>
  <template v-else><header class="site-nav"><div class="nav-inner"><router-link :to="auth.user?.role==='ADMIN'?'/admin/users':'/resumes'" class="brand" aria-label="面序首页">面序<span class="brand-mark"/></router-link><nav v-if="auth.user" class="main-links platform-links" aria-label="主导航"><template v-if="auth.user.role==='STUDENT'"><router-link v-for="[path,label] in studentLinks" :key="path" :to="path" :class="{'router-link-active':route.path.startsWith(path+'/')}">{{label}}</router-link></template><template v-else><router-link to="/admin/users">账号管理</router-link><router-link to="/admin/content">内容与规则</router-link></template><router-link to="/profile">个人资料</router-link></nav><el-dropdown v-if="auth.user" trigger="click" @command="(command:string)=>command==='logout'?logout():router.push('/profile')"><button class="user-menu"><span class="avatar">{{auth.user.nickname.slice(0,1)}}</span><span class="user-name">{{auth.user.nickname}}</span><span class="chevron">⌄</span></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="profile">个人资料</el-dropdown-item><el-dropdown-item command="logout" divided>退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown><router-link v-else :to="route.path==='/register'?'/login':'/register'" class="nav-auth">{{route.path==='/register'?'已有账号？登录':'创建学生账号 ↗'}}</router-link></div></header>
  <router-view :key="String(route.name||route.path)"/>
  <footer class="site-footer"><span>面序 · 让准备，更进一步。</span><span>认真整理经历，从容面对机会。</span></footer></template></el-config-provider>
</template>

<style scoped>
.platform-links{gap:clamp(13px,2vw,26px);margin-right:0;min-width:0;overflow:auto;scrollbar-width:thin}
.platform-links a{flex:none}
@media(max-width:840px){
  .site-nav:has(.platform-links){height:auto}
  .nav-inner:has(.platform-links){flex-wrap:wrap;gap:0 16px;padding-top:12px}
  .nav-inner:has(.platform-links)>.el-dropdown{margin-left:auto}
  .platform-links{order:3;width:100%;height:48px;justify-content:flex-start;margin:0}
  .platform-links .router-link-active::after{bottom:6px}
}
</style>
