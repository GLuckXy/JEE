import {createRouter,createWebHashHistory} from 'vue-router'
import {useAuth} from './stores/auth'
import {TOKEN_KEY} from './api'
export const router=createRouter({history:createWebHashHistory(),routes:[
  {path:'/login',component:()=>import('./views/AuthView.vue'),meta:{public:true}},
  {path:'/register',component:()=>import('./views/AuthView.vue'),meta:{public:true}},
  {path:'/',redirect:'/resumes'},
  {path:'/resumes',component:()=>import('./views/ResumesView.vue'),meta:{student:true}},
  {path:'/resumes/:id',component:()=>import('./views/EditorView.vue'),meta:{student:true}},
  {path:'/projects',component:()=>import('./views/ProjectsView.vue'),meta:{student:true}},
  {path:'/demos',component:()=>import('./views/DemosView.vue'),meta:{student:true}},
  {path:'/training',component:()=>import('./modules/training/TrainingHome.vue'),props:{mode:'NORMAL'},meta:{student:true,navigationPath:'/training'}},
  {path:'/training/:id',component:()=>import('./modules/training/TrainingSession.vue'),props:{mode:'NORMAL'},meta:{student:true,navigationPath:'/training'}},
  {path:'/practice',component:()=>import('./modules/training/TrainingHome.vue'),props:{mode:'SPECIAL'},meta:{student:true,navigationPath:'/training'}},
  {path:'/practice/:id',component:()=>import('./modules/training/TrainingSession.vue'),props:{mode:'SPECIAL'},meta:{student:true,navigationPath:'/training'}},
  {path:'/formal',component:()=>import('./modules/formal/FormalInterview.vue'),meta:{student:true}},
  {path:'/reports',component:()=>import('./views/ReportsView.vue'),meta:{student:true}},
  {path:'/reports/:id',component:()=>import('./views/ReportsView.vue'),meta:{student:true}},
  {path:'/profile',component:()=>import('./views/ProfileView.vue')},
  {path:'/admin/users',component:()=>import('./views/AdminView.vue'),meta:{admin:true}},
  {path:'/admin/content',component:()=>import('./views/ContentAdminView.vue'),meta:{admin:true}},
  {path:'/:pathMatch(.*)*',redirect:'/'}
]})
router.beforeEach(async to=>{const auth=useAuth();if(sessionStorage.getItem(TOKEN_KEY)&&!auth.user){try{await auth.restore()}catch{if(sessionStorage.getItem(TOKEN_KEY))return true}}if(!to.meta.public&&!sessionStorage.getItem(TOKEN_KEY))return{path:'/login',query:{redirect:to.fullPath}};if(to.meta.public&&auth.user)return auth.user.role==='ADMIN'?'/admin/users':'/resumes';if(to.meta.admin&&auth.user?.role!=='ADMIN')return'/resumes';if(to.meta.student&&auth.user?.role==='ADMIN')return'/admin/users'})
