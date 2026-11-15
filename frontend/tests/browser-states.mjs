import {chromium} from '@playwright/test'
import {mkdir,writeFile} from 'node:fs/promises'
import assert from 'node:assert/strict'
const base=process.env.FRONTEND_URL||'http://127.0.0.1:5173',output=`test-results/states-${new Date().toISOString().replace(/[:.]/g,'-')}`
await mkdir(output,{recursive:true})
const checks=[]
const check=(name,condition=true)=>{assert.ok(condition,name);checks.push(name);console.log(`PASS ${name}`)}
const browser=await chromium.launch({channel:'msedge',headless:true})
const context=await browser.newContext({viewport:{width:1440,height:1040}})
const user={id:'fixture-student',account:'sample001',nickname:'样例学生',role:'STUDENT',status:'ACTIVE',email:null,phone:null,school:null,major:null,createdAt:'2026-11-14T10:00:00',updatedAt:'2026-11-14T10:00:00'}
const admin={...user,id:'fixture-admin',account:'admin001',nickname:'平台管理员',role:'ADMIN'}
const page=await context.newPage();page.setDefaultTimeout(12000)
let statusCalls=0,mode='admin',errorStatus=409
const payload={education:[],skills:['Java'],internships:[],projects:[{name:'样例项目',goal:'',responsibility:'真实职责',techStack:[],difficulty:'',solution:'',result:'',improvement:''}]}
const detail={summary:{id:'fixture-resume',fileId:'fixture-file',originalName:'样例.pdf',parseStatus:'FAILED',confirmStatus:'DRAFT',version:0,revision:1,failureReason:'此样例没有可提取文本，请手动补充。',createdAt:'2026-11-14T10:00:00',confirmedAt:null},rawParseResult:null,draft:payload,confirmedPayload:null,allowedActions:['SAVE_DRAFT','CONFIRM']}
await context.addInitScript(()=>sessionStorage.setItem('mianxu.session','fixture-session-for-ui-only'))
await page.route('**/api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname,method=req.method()
  const ok=data=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:'OK',message:'成功',data})})
  if(path==='/api/users/me')return ok(mode==='admin'?admin:user)
  if(path==='/api/admin/users')return ok({total:1,page:1,size:10,records:[user]})
  if(path==='/api/admin/users/fixture-student/status'){statusCalls++;user.status=req.postDataJSON().status;return ok(user)}
  if(path==='/api/admin/users/fixture-student'&&method==='PUT'){user.nickname=req.postDataJSON().nickname;return ok(user)}
  if(path==='/api/admin/users/fixture-student')return ok(user)
  if(path==='/api/resumes/fixture-resume')return ok(detail)
  if(path==='/api/resumes/fixture-resume/parsed-data')return route.fulfill({status:errorStatus,contentType:'application/json',body:JSON.stringify({code:errorStatus===401?'AUTH_REQUIRED':'VERSION_CONFLICT',message:errorStatus===401?'登录已过期':'资料已变化，请重新加载',data:null})})
  return route.fulfill({status:404,contentType:'application/json',body:JSON.stringify({code:'NOT_FOUND',message:'样例未提供此接口',data:null})})
})
const report={mockedApi:true,checks,output}
try{
  await page.goto(base+'/#/admin/users')
  await page.getByRole('heading',{name:'让账户管理，清晰有界。'}).waitFor()
  check('管理员刷新直达管理页，角色恢复正确')
  await page.getByRole('button',{name:'禁用',exact:true}).click()
  await page.getByRole('button',{name:'取消',exact:true}).click()
  check('取消禁用不会发出修改请求',statusCalls===0)
  await page.getByRole('button',{name:'禁用',exact:true}).click()
  await page.getByRole('button',{name:'确认禁用',exact:true}).click()
  await page.getByRole('button',{name:'恢复',exact:true}).waitFor()
  check('确认后才发送禁用请求并更新表格',statusCalls===1&&user.status==='DISABLED')
  await page.getByRole('button',{name:'修改昵称',exact:true}).click()
  await page.getByLabel('昵称',{exact:true}).fill('更新后的昵称')
  await page.getByRole('button',{name:'保存昵称',exact:true}).click()
  await page.getByRole('cell',{name:'更新后的昵称',exact:true}).waitFor()
  check('管理员昵称编辑使用允许字段')
  await page.getByRole('button',{name:'详情',exact:true}).click()
  await page.getByText('账号基础资料',{exact:true}).waitFor()
  check('账号详情抽屉可用')
  await page.locator('.el-drawer__close-btn').click()
  await page.screenshot({path:output+'/admin.png',fullPage:true,animations:'disabled'})
  await page.setViewportSize({width:390,height:844})
  check('390px管理员页表格内部滚动，无页面溢出',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  mode='student';await page.setViewportSize({width:1440,height:1040});await page.goto(base+'/?fixture=student#/resumes/fixture-resume')
  await page.getByText('此样例没有可提取文本，请手动补充。',{exact:true}).waitFor()
  check('解析失败提示与人工编辑入口可用')
  await page.getByLabel('本人职责 *',{exact:true}).fill('我需要保留的本地修改')
  await page.getByRole('button',{name:'保存草稿',exact:true}).click()
  await page.getByText('资料已在别处更新。当前编辑内容仍在，请先复制需要保留的文字，再重新加载最新版本。',{exact:true}).waitFor()
  check('冲突不覆盖本地编辑',(await page.getByLabel('本人职责 *',{exact:true}).inputValue())==='我需要保留的本地修改')
  check('冲突时禁用确认按钮',await page.getByRole('button',{name:'确认简历',exact:true}).isDisabled())
  await page.getByRole('button',{name:'重新加载最新版本',exact:true}).click()
  await page.getByRole('button',{name:'保留修改',exact:true}).click()
  check('冲突重新加载可取消并保留内容',(await page.getByLabel('本人职责 *',{exact:true}).inputValue())==='我需要保留的本地修改')
  await page.screenshot({path:output+'/conflict.png',fullPage:true,animations:'disabled'})
  errorStatus=401
  await page.getByRole('button',{name:'保存草稿',exact:true}).click()
  await page.waitForURL(/#\/login$/)
  check('有未保存修改时，会话过期仍返回登录页')
  check('会话过期清理本地令牌',await page.evaluate(()=>sessionStorage.getItem('mianxu.session')===null))
  report.success=true
}catch(e){report.success=false;report.failure=e.message;console.error(e.message);await page.screenshot({path:output+'/failure.png',fullPage:true,animations:'disabled'});process.exitCode=1}
finally{await writeFile(output+'/report.json',JSON.stringify(report,null,2));await browser.close();console.log(`Report ${output}/report.json · ${checks.length} passed · mocked API, no database mutation`)}


