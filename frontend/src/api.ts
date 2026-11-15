import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { User,Page,Summary,Detail,Payload,Project,ResumeVersion,Selection } from './types'
export const TOKEN_KEY='mianxu.session'
export class ApiError extends Error { constructor(public code:string,message:string,public status:number) {super(message)} }
const http=axios.create({baseURL:'/api',timeout:30000})
http.interceptors.request.use(config=>{const token=sessionStorage.getItem(TOKEN_KEY);if(token) config.headers.Authorization=`Bearer ${token}`;return config})
http.interceptors.response.use(response=>response,async(error:unknown)=>{
  if(!axios.isAxiosError(error))return Promise.reject(error)
  let body=error.response?.data
  if(body instanceof Blob){try{body=JSON.parse(await body.text())}catch{body=null}}
  const status=error.response?.status||0
  const failure=new ApiError(body?.code||'NETWORK_ERROR',body?.message||(status===0?'无法连接服务，请检查网络或后端是否已启动。':'请求失败，请稍后重试。'),status)
  const authEndpoint=/\/auth\/(login|register)$/.test(error.config?.url||'')
  if((status===401||failure.code==='ACCOUNT_DISABLED')&&!authEndpoint){sessionStorage.removeItem(TOKEN_KEY);window.dispatchEvent(new Event('session-expired'))}
  return Promise.reject(failure)
})
async function request<T>(method:string,url:string,data?:unknown,params?:unknown):Promise<T>{const response=await http.request({method,url,data,params});return response.data.data as T}
export function showError(e:unknown){ElMessage.error(e instanceof Error?e.message:'操作失败，请重试。')}
export const api={
  register:(data:{account:string;password:string;nickname:string})=>request<User>('POST','/auth/register',data),
  login:(data:{account:string;password:string})=>request<{accessToken:string;user:User}>('POST','/auth/login',data),
  logout:()=>request<void>('POST','/auth/logout'), me:()=>request<User>('GET','/users/me'),
  profile:(data:Pick<User,'nickname'|'email'|'phone'|'school'|'major'>)=>request<User>('PUT','/users/me',data),
  resumes:(page=1,size=8)=>request<Page<Summary>>('GET','/resumes',undefined,{page,size}),
  detail:(id:string)=>request<Detail>('GET',`/resumes/${id}`),
  deleteResume:(id:string,expectedRevision:number)=>request<void>('DELETE',`/resumes/${id}`,{expectedRevision}),
  upload:async(file:File,onProgress:(value:number)=>void)=>{const form=new FormData();form.append('file',file);const response=await http.post('/resumes',form,{timeout:180000,onUploadProgress:e=>onProgress(e.total?Math.min(99,Math.round(e.loaded/e.total*100)):0)});return response.data.data as Detail},
  draft:(id:string,expectedRevision:number,payload:Payload)=>request<Detail>('PUT',`/resumes/${id}/parsed-data`,{expectedRevision,payload}),
  confirm:(id:string,expectedRevision:number)=>request<Detail>('POST',`/resumes/${id}/confirm`,{expectedRevision}),
  retry:(id:string)=>http.post(`/resumes/${id}/retry-parse`,undefined,{timeout:180000}).then(r=>r.data.data as Detail),
  versions:(id:string)=>request<ResumeVersion[]>('GET',`/resumes/${id}/versions`),
  projects:(id:string)=>request<Project[]>('GET',`/resumes/${id}/projects`),
  select:(id:string,projectId:string,version:number)=>request<Selection>('POST',`/resumes/${id}/select-project`,{projectId,version}),
  file:(id:string)=>http.get(`/files/${id}`,{responseType:'blob'}).then(r=>r.data as Blob),
  users:(page=1,size=10)=>request<Page<User>>('GET','/admin/users',undefined,{page,size}),
  user:(id:string)=>request<User>('GET',`/admin/users/${id}`),
  userNickname:(id:string,nickname:string)=>request<User>('PUT',`/admin/users/${id}`,{nickname}),
  userStatus:(id:string,status:User['status'])=>request<User>('PUT',`/admin/users/${id}/status`,{status})
}
export async function downloadFile(id:string,name:string){const blob=await api.file(id);const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
