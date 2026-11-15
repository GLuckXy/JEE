import {chromium} from '@playwright/test'
import {randomBytes} from 'node:crypto'
import {mkdir,writeFile} from 'node:fs/promises'
import assert from 'node:assert/strict'
const base=process.env.FRONTEND_URL||'http://127.0.0.1:5173',output=`test-results/resume-live-${Date.now()}`
await mkdir(output,{recursive:true})
const browser=await chromium.launch({channel:'msedge',headless:true}),context=await browser.newContext({viewport:{width:1440,height:1040}}),page=await context.newPage()
page.setDefaultTimeout(15000)
const checks=[],errors=[],report={realApi:true,checks,errors,output}
const check=(name,condition=true)=>{assert.ok(condition,name);checks.push(name);console.log(`PASS ${name}`)}
page.on('pageerror',e=>errors.push(e.message))
const account=`rv${randomBytes(5).toString('hex')}`,password=randomBytes(20).toString('base64url')+'Aa1'
let token
async function call(method,path,data,expected=200){const res=await context.request.fetch(base+'/api'+path,{method,data,headers:token?{Authorization:`Bearer ${token}`}:{}});assert.equal(res.status(),expected,`${method} ${path}: HTTP ${res.status()}`);return (await res.json()).data}
function pdf(){
 const lines=['Education','2023.09 - 2027.06 Sample University Bachelor','Internships','2026.01 - 2026.06 Sample Inc Java Developer','Projects','ProjectName: Interview Platform','Goal: Practice interviews','Responsibility: Implement account APIs','TechStack: Java, MySQL','ProjectName: Course System','Goal: Course enrollment','Responsibility: Implement enrollment APIs']
 const stream='BT /F1 12 Tf 50 760 Td '+lines.map((line,i)=>(i?'0 -22 Td ':'')+`(${line}) Tj`).join(' ')+' ET'
 const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`]
 let doc='%PDF-1.4\n';const offsets=[]
 objects.forEach((o,i)=>{offsets.push(Buffer.byteLength(doc));doc+=`${i+1} 0 obj\n${o}\nendobj\n`})
 const xref=Buffer.byteLength(doc);doc+=`xref\n0 6\n0000000000 65535 f \n${offsets.map(v=>String(v).padStart(10,'0')+' 00000 n ').join('\n')}\ntrailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`
 return Buffer.from(doc)
}
try{
 await call('POST','/auth/register',{account,password,nickname:'简历增量联调'})
 token=(await call('POST','/auth/login',{account,password})).accessToken
 const upload=await context.request.post(base+'/api/resumes',{headers:{Authorization:`Bearer ${token}`},multipart:{file:{name:'resume-update-live.pdf',mimeType:'application/pdf',buffer:pdf()}}})
 assert.equal(upload.status(),200)
 let detail=(await upload.json()).data
 const id=detail.summary.id,fid=detail.summary.fileId;report.resumeId=id;report.fileId=fid
 check('真实PDF解析使用新版规则',detail.rawParseResult.parserVersion==='sections-v2')
 const parsed=detail.rawParseResult.extracted
 check('教育基础字段正确提取',parsed.education[0].organization==='Sample University'&&parsed.education[0].title==='Bachelor'&&parsed.education[0].period.includes('2023'))
 check('实习基础字段正确提取',parsed.internships[0].organization==='Sample Inc'&&parsed.internships[0].title.includes('Developer'))
 check('区分两个项目并提取本人职责',parsed.projects.length===2&&parsed.projects[0].responsibility==='Implement account APIs')
 const draft=structuredClone(parsed);draft.education[0].title='';draft.internships[0].title='';draft.projects[0].goal='';draft.projects[0].responsibility='本人手填内容必须保留'
 detail=await call('PUT',`/resumes/${id}/parsed-data`,{expectedRevision:detail.summary.revision,payload:draft})
 detail=await call('POST',`/resumes/${id}/retry-parse`)
 check('重新解析不覆盖已保存草稿',detail.draft.projects[0].responsibility==='本人手填内容必须保留'&&detail.draft.education[0].title==='')
 await context.addInitScript(value=>sessionStorage.setItem('mianxu.session',value),token)
 await page.goto(base+`/#/resumes/${id}`)
 await page.getByRole('button',{name:'查看解析候选与填表建议',exact:true}).click()
 await page.getByRole('button',{name:'填入空白字段',exact:true}).click()
 await page.getByRole('button',{name:'确认填入空白',exact:true}).click()
 check('页面候选填入教育和实习字段',await page.getByLabel('专业 / 学位',{exact:true}).inputValue()==='Bachelor'&&(await page.getByLabel('岗位',{exact:true}).inputValue()).includes('Developer'))
 check('页面合并保留手填职责',await page.getByLabel('本人职责 *',{exact:true}).first().inputValue()==='本人手填内容必须保留')
 await page.getByRole('button',{name:'保存草稿',exact:true}).click()
 await page.getByText('草稿已保存，确认后才可用于面试',{exact:true}).waitFor()
 detail=await call('GET',`/resumes/${id}`)
 check('填表后实际持久化到MySQL',detail.draft.education[0].title==='Bachelor'&&detail.draft.internships[0].title.includes('Developer'))
 await page.getByRole('button',{name:'确认简历',exact:true}).click()
 await page.getByRole('button',{name:'确认并保存版本',exact:true}).click()
 await page.getByRole('button',{name:'选择项目',exact:true}).waitFor()
 const projects=await call('GET',`/resumes/${id}/projects`);report.projectId=projects[0].id
 check('确认后生成两个可选项目',projects.length===2)
 await page.getByRole('button',{name:'删除简历',exact:true}).click()
 await page.getByRole('button',{name:'保留简历',exact:true}).click()
 check('取消删除保持可访问',!!(await call('GET',`/resumes/${id}`)))
 await page.getByRole('button',{name:'删除简历',exact:true}).click()
 await page.getByRole('button',{name:'确认删除',exact:true}).click()
 await page.waitForURL(/#\/resumes$/)
 await page.getByText('还没有简历。从你的第一份经历开始。',{exact:true}).waitFor()
 check('真实删除后列表刷新为空',(await call('GET','/resumes')).total===0)
 for(const path of [`/resumes/${id}`,`/resumes/${id}/versions`,`/resumes/${id}/projects`,`/files/${fid}`,`/resume-projects/${projects[0].id}`])await call('GET',path,undefined,404)
 await call('POST',`/resumes/${id}/select-project`,{projectId:projects[0].id,version:1},404)
 check('删除后详情、版本、项目、原文件和选择接口全部拒绝访问')
 check('无浏览器运行异常',errors.length===0)
 report.success=true
}catch(e){report.success=false;console.error(e.message);report.failure=e.message.replaceAll(password,'[redacted]');process.exitCode=1;await page.screenshot({path:output+'/failure.png',fullPage:true})}
finally{if(token)await call('POST','/auth/logout').catch(()=>{});await writeFile(output+'/report.json',JSON.stringify(report,null,2));await browser.close();console.log(`Report ${output}/report.json · ${checks.length} passed · only synthetic resume logically deleted, files retained`)}
