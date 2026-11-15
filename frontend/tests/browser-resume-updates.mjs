import {chromium} from '@playwright/test'
import {mkdir,writeFile} from 'node:fs/promises'
import assert from 'node:assert/strict'
const output=`test-results/resume-updates-${Date.now()}`,checks=[]
await mkdir(output,{recursive:true})
const browser=await chromium.launch({channel:'msedge',headless:true})
const context=await browser.newContext({viewport:{width:1440,height:1040}})
await context.addInitScript(()=>sessionStorage.setItem('mianxu.session','fixture-session-for-ui-only'))
const page=await context.newPage();page.setDefaultTimeout(12000)
const errors=[];page.on('pageerror',e=>errors.push(e.message))
const check=(name,value=true)=>{assert.ok(value,name);checks.push(name);console.log(`PASS ${name}`)}
const project={name:'面试平台',responsibility:'本人原有职责',goal:'',techStack:[],difficulty:'',solution:'',result:'',improvement:''}
const draft={skills:[],education:[],internships:[],projects:[project]}
const extracted={...draft,skills:['Java'],education:[{organization:'测试大学',title:'软件工程 / 本科',period:'2023—2027',description:''}],projects:[{...project,responsibility:'解析候选职责',goal:'帮助同学练习面试'}]}
const summary={id:'fixture-resume',fileId:'fixture-file',originalName:'误上传样例.pdf',parseStatus:'SUCCESS',confirmStatus:'DRAFT',version:0,revision:3,failureReason:null,createdAt:'2026-11-14T10:00:00',confirmedAt:null}
const detail={summary,draft,rawParseResult:{text:'教育背景\n测试大学 软件工程 本科',extracted,parserVersion:'sections-v2',warnings:['字段仍需本人核对'],evidence:[{field:'education[0].organization',source:'测试大学 软件工程 本科',confidence:'REVIEW'}]},allowedActions:['SAVE_DRAFT','CONFIRM','DELETE']}
let removed=false,deletes=0,saves=0
await page.route('**/api/**',async route=>{
 const req=route.request(),path=new URL(req.url()).pathname
 const ok=data=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:'OK',message:'成功',data})})
 if(path==='/api/users/me')return ok({id:'student',account:'sample001',nickname:'测试学生',role:'STUDENT',status:'ACTIVE'})
 if(path==='/api/resumes/fixture-resume'&&req.method()==='DELETE'){assert.equal(req.postDataJSON().expectedRevision,3);deletes++;removed=true;return ok(null)}
 if(path==='/api/resumes/fixture-resume/parsed-data'){saves++;return ok(detail)}
 if(path==='/api/resumes/fixture-resume')return ok(detail)
 if(path==='/api/resumes')return ok({records:removed?[]:[summary],total:removed?0:1,page:1,size:8})
 return route.fulfill({status:404,body:'{}'})
})
try{
 await page.goto((process.env.FRONTEND_URL||'http://127.0.0.1:5173')+'/#/resumes/fixture-resume')
 await page.getByRole('button',{name:'查看解析候选与填表建议',exact:true}).click()
 await page.getByRole('button',{name:'填入空白字段',exact:true}).click()
 await page.getByRole('button',{name:'再检查一下',exact:true}).click()
 check('取消填入不会保存草稿',saves===0)
 await page.getByRole('button',{name:'填入空白字段',exact:true}).click()
 await page.getByRole('button',{name:'确认填入空白',exact:true}).click()
 await page.getByLabel('学校',{exact:true}).waitFor()
 check('填入教育基础字段',await page.getByLabel('学校',{exact:true}).inputValue()==='测试大学')
 check('保留已有本人职责',await page.getByLabel('本人职责 *',{exact:true}).inputValue()==='本人原有职责')
 check('填入项目空白字段',await page.getByLabel('项目目标',{exact:true}).inputValue()==='帮助同学练习面试')
 check('应用后只改页面，不自动保存',saves===0)
 await page.getByRole('button',{name:'查看解析候选与填表建议',exact:true}).click()
 await page.getByText('替换全部草稿',{exact:true}).click()
 await page.getByRole('cell',{name:'本人原有职责',exact:true}).waitFor()
 await page.getByRole('button',{name:'应用替换结果',exact:true}).click()
 await page.getByRole('button',{name:'确认替换草稿',exact:true}).click()
 check('查看差异并确认后替换',await page.getByLabel('本人职责 *',{exact:true}).inputValue()==='解析候选职责')
 await page.setViewportSize({width:390,height:844})
 await page.getByRole('button',{name:'查看解析候选与填表建议',exact:true}).click()
 await page.getByText('字段来源原文（未修改解析候选的依据）',{exact:true}).click()
 await page.getByText('测试大学 软件工程 本科',{exact:true}).last().waitFor()
 check('移动端候选面板无页面溢出',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
 await page.screenshot({path:output+'/mobile-analysis.png',fullPage:true})
 await page.locator('.el-drawer__close-btn:visible').click()
 await page.getByRole('button',{name:'删除简历',exact:true}).click()
 await page.getByRole('button',{name:'保留简历',exact:true}).click()
 check('取消删除不发送请求',deletes===0)
 await page.getByRole('button',{name:'删除简历',exact:true}).click()
 await page.getByRole('button',{name:'确认删除',exact:true}).click()
 await page.waitForURL(/#\/resumes$/)
 await page.getByText('还没有简历。从你的第一份经历开始。',{exact:true}).waitFor()
 check('携带修订号删除后返回列表并刷新',deletes===1)
 check('没有浏览器运行异常',errors.length===0)
}catch(e){console.error(e);process.exitCode=1;await page.screenshot({path:output+'/failure.png',fullPage:true})}
finally{await writeFile(output+'/report.json',JSON.stringify({mockedApi:true,checks,errors,success:!process.exitCode},null,2));await browser.close();console.log(`Report ${output}/report.json · ${checks.length} passed`)}
