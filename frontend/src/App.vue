<script setup lang="ts">
import {onMounted,onBeforeUnmount,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useAuth} from './stores/auth'
import {showError,TOKEN_KEY} from './api'
const auth=useAuth(),route=useRoute(),router=useRouter(),ready=ref(false),startupError=ref(false)
const studentLinks = [
  [['/resumes', '我的简历'], ['/projects', '项目资料'], ['/demos', '典型示范']],
  [['/training', '模拟训练'], ['/formal', '正式模拟']],
  [['/reports', '报告与历史']],
]
const scrolled = ref(false)
const updateScrolled = () => { scrolled.value = window.scrollY > 4 }
async function initialize(){startupError.value=false;try{await auth.restore();ready.value=true;if(auth.user&&route.meta.public)await router.replace(auth.user.role==='ADMIN'?'/admin/users':'/resumes')}catch(e){if(!sessionStorage.getItem(TOKEN_KEY)){auth.clear();ready.value=true;await router.replace('/login')}else{startupError.value=true;showError(e)}}}
function expired(){auth.clear();ElMessage.warning('登录已失效，请重新登录。');void router.replace('/login')}
async function logout(){try{await ElMessageBox.confirm('退出当前账户？未保存的内容将不会保留。','退出登录',{confirmButtonText:'退出',cancelButtonText:'取消'});await auth.logout();await router.replace('/login')}catch(e){if(e!=='cancel'&&e!=='close')showError(e)}}
onMounted(()=>{window.addEventListener('session-expired',expired);void initialize()})
onBeforeUnmount(()=>window.removeEventListener('session-expired',expired))
onMounted(() => {
  updateScrolled()
  window.addEventListener('scroll', updateScrolled, { passive: true })
})
onBeforeUnmount(() => window.removeEventListener('scroll', updateScrolled))
</script>
<template>
  <el-config-provider>
    <div v-if="!ready" class="startup">
      <div class="brand">面序<span class="brand-mark" /></div>
      <p>{{ startupError ? '暂时无法连接服务。' : '正在准备你的工作台…' }}</p>
      <el-button v-if="startupError" @click="initialize">重新连接</el-button>
    </div>

    <template v-else>
      <header class="site-nav" :class="{ scrolled }">
        <div class="nav-inner">
          <div class="nav-brand">
            <router-link :to="auth.user?.role === 'ADMIN' ? '/admin/users' : '/resumes'" class="brand" aria-label="面序首页">
              面序<span class="brand-mark" />
            </router-link>
            <span class="status-pill formal-focus-status" role="status">正式模拟进行中</span>
          </div>

          <nav v-if="auth.user" class="main-links" aria-label="主导航">
            <template v-if="auth.user.role === 'STUDENT'">
              <template v-for="(group, index) in studentLinks" :key="group[0][0]">
                <span v-if="index > 0" class="nav-divider" aria-hidden="true" />
                <router-link
                  v-for="[path, label] in group"
                  :key="path"
                  :to="path"
                  :class="{ 'router-link-active': route.meta.navigationPath === path || route.path.startsWith(path + '/') }"
                >
                  {{ label }}
                </router-link>
              </template>
            </template>
            <template v-else>
              <router-link to="/admin/users">账号管理</router-link>
              <router-link to="/admin/content">内容与规则</router-link>
            </template>
          </nav>

          <el-dropdown
            v-if="auth.user"
            trigger="click"
            popper-class="account-menu"
            @command="(command: string) => command === 'logout' ? logout() : router.push('/profile')"
          >
            <button class="user-menu" :aria-label="'账户菜单：' + auth.user.nickname">
              <span class="avatar">{{ auth.user.nickname.slice(0, 1) }}</span>
              <span class="user-name">{{ auth.user.nickname }}</span>
              <span class="chevron" aria-hidden="true">⌄</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <router-view v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" :key="String(route.name || route.path)" />
        </Transition>
      </router-view>

      <footer class="site-footer">面序 · 简历与项目资料仅对本人可见</footer>
    </template>
  </el-config-provider>
</template>
