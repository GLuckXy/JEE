import { chromium } from '@playwright/test'
import { randomBytes } from 'node:crypto'
import { mkdir,readFile,writeFile } from 'node:fs/promises'
import assert from 'node:assert/strict'

const base=process.env.FRONTEND_URL||'http://127.0.0.1:5173'
const output=`test-results/browser-${new Date().toISOString().replace(/[:.]/g,'-')}`
await mkdir(output,{recursive:true})
const checks=[]
function check(name,condition=true){assert.ok(condition,name);checks.push(name);console.log(`PASS ${name}`)}
function pdf(text=true){const stream=text?'BT /F1 12 Tf 50 700 Td (Skills) Tj 0 -20 Td (Java, SQL) Tj 0 -20 Td (Projects) Tj 0 -20 Td (Frontend Verification) Tj ET':'';const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`];let doc='%PDF-1.4\n';const offsets=[];objects.forEach((o,i)=>{offsets.push(Buffer.byteLength(doc));doc+=`${i+1} 0 obj\n${o}\nendobj\n`});const xref=Buffer.byteLength(doc);doc+=`xref\n0 6\n0000000000 65535 f \n${offsets.map(v=>String(v).padStart(10,'0')+' 00000 n ').join('\n')}\ntrailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;return Buffer.from(doc)}
const browser=await chromium.launch({channel:'msedge',headless:true})
const context=await browser.newContext({viewport:{width:1440,height:1040},acceptDownloads:true})
const page=await context.newPage()
const errors=[]
page.on('pageerror',e=>errors.push(e.message))
page.setDefaultTimeout(20000)
const account=`ui${randomBytes(5).toString('hex')}`,password=randomBytes(20).toString('base64url')+'Aa1'
const meta={base,account,checks,output}
try{
  await page.goto(base+'/#/register')
  await page.getByLabel('账号',{exact:true}).fill(account)
  await page.getByLabel('昵称',{exact:true}).fill('界面联调学生')
  await page.getByLabel('密码',{exact:true}).fill(password)
  await page.getByLabel('确认密码',{exact:true}).fill(password)
  await page.getByRole('button',{name:/创建账号/}).click()
  await page.waitForURL('**/#/login')
  check('通过页面注册真实学生账号')
  await page.getByLabel('账号',{exact:true}).fill(account)
  await page.getByLabel('密码',{exact:true}).fill(password)
  await page.getByRole('button',{name:/登录面序/}).click()
  await page.waitForURL('**/#/resumes')
  await page.getByRole('heading',{name:'我的简历',exact:true}).waitFor()
  check('页面登录并进入真实工作台')
  await page.getByText('还没有简历。从你的第一份经历开始。').waitFor()
  check('空数据展示真实空状态')
  await page.screenshot({path:`${output}/01-workspace-empty.png`,fullPage:true,animations:'disabled'})
  await page.getByRole('button',{name:'上传简历',exact:true}).click()
  const dialog=page.getByRole('dialog')
  await dialog.locator('input[type=file]').setInputFiles({name:'界面联调.pdf',mimeType:'application/pdf',buffer:pdf()})
  await dialog.getByRole('button',{name:'上传并解析',exact:true}).click()
  await page.waitForURL(/#\/resumes\/[a-f0-9-]+/)
  await page.locator('.source-text').filter({hasText:'Frontend Verification'}).waitFor()
  meta.resumeId=page.url().split('/').pop()
  check('通过页面上传PDF并读取真实解析文本')
  await page.getByLabel('项目名称 *',{exact:true}).fill('前端全流程验证项目')
  await page.getByLabel('本人职责 *',{exact:true}).fill('本人完成上传校对、确认和项目选择的浏览器联调')
  await page.getByRole('button',{name:'保存草稿',exact:true}).click()
  await page.getByText('草稿已保存，确认后才可用于面试',{exact:true}).waitFor()
  check('草稿实际保存到MySQL')
  await page.screenshot({path:`${output}/02-editor.png`,fullPage:true,animations:'disabled'})
  await page.getByRole('button',{name:'确认简历',exact:true}).click()
  await page.getByRole('button',{name:'确认并保存版本',exact:true}).click()
  await page.getByText('简历已确认，可以选择项目了',{exact:true}).waitFor()
  check('页面确认简历生成版本')
  await page.getByRole('button',{name:'版本记录',exact:true}).click()
  await page.getByText('历史确认内容仅供查看，不会自动替换当前草稿。').waitFor()
  await page.locator('.payload-readonly').getByText('前端全流程验证项目',{exact:true}).waitFor()
  check('历史确认版本可读取')
  await page.locator('.el-drawer__close-btn:visible').click()
  const downloadPromise=page.waitForEvent('download')
  await page.getByRole('button',{name:'原文件',exact:true}).click()
  const download=await downloadPromise
  check('原文件鉴权下载',download.suggestedFilename()==='界面联调.pdf')
  await page.getByRole('button',{name:'选择项目',exact:true}).click()
  await page.locator('.project-detail h2').filter({hasText:'前端全流程验证项目'}).waitFor()
  await page.getByRole('button',{name:'选择这个项目',exact:true}).click()
  await page.getByRole('heading',{name:'项目已选好。'}).waitFor()
  check('项目选择成功并显示后端确认快照')
  await page.screenshot({path:`${output}/03-project.png`,fullPage:true,animations:'disabled'})
  const snapshotPromise=page.waitForEvent('download')
  await page.getByRole('button',{name:'下载项目资料',exact:true}).click()
  const projectDownload=await snapshotPromise
  check('可下载本次项目JSON快照',projectDownload.suggestedFilename().endsWith('.json'))
  const selectedProject=JSON.parse(await readFile(await projectDownload.path(),'utf8'))
  const practiceLink=page.getByRole('link',{name:'去普通训练 →',exact:true})
  check('已选项目提供普通训练入口',(await practiceLink.getAttribute('href'))==='#/training')
  await page.getByRole('link',{name:'用该项目开始正式模拟 →',exact:true}).click()
  await page.waitForURL(url=>url.hash.startsWith('#/formal?') &&
    new URLSearchParams(url.hash.split('?')[1]).get('projectId')===selectedProject.projectId)
  await page.locator('.formal-choices').getByText(selectedProject.snapshot.name,{exact:true}).waitFor()
  check('选择项目后直达正式模拟并自动预选该项目')
  await page.locator('.main-links').getByRole('link',{name:'个人资料',exact:true}).click()
  await page.getByLabel('学校（选填）',{exact:true}).fill('浏览器验证学校')
  await page.getByRole('button',{name:'保存资料',exact:true}).click()
  await page.getByText('个人资料已保存',{exact:true}).waitFor()
  await page.reload()
  await page.getByLabel('学校（选填）',{exact:true}).waitFor()
  check('个人资料保存并刷新保留',(await page.getByLabel('学校（选填）',{exact:true}).inputValue())==='浏览器验证学校')
  await page.goto(base+'/#/admin/users')
  await page.waitForURL('**/#/resumes')
  check('学生无法打开管理员页面')
  await page.locator('.resume-card').first().waitFor()
  await page.screenshot({path:`${output}/04-workspace.png`,fullPage:true,animations:'disabled'})
  const token=await page.evaluate(()=>sessionStorage.getItem('mianxu.session'))
  const otherAccount=`ui${randomBytes(5).toString('hex')}`
  const registration=await context.request.post(base+'/api/auth/register',{data:{account:otherAccount,password,nickname:'第二界面测试账号'}})
  check('准备第二个独立学生账号',registration.ok())
  const login=await context.request.post(base+'/api/auth/login',{data:{account:otherAccount,password}})
  const secondToken=(await login.json()).data.accessToken
  const cross=await context.request.get(base+`/api/resumes/${meta.resumeId}`,{headers:{Authorization:`Bearer ${secondToken}`}})
  check('第二账号无法读取该简历',cross.status()===404)
  await context.request.post(base+'/api/auth/logout',{headers:{Authorization:`Bearer ${secondToken}`}})
  await page.setViewportSize({width:390,height:844})
  check('390px工作台无横向溢出',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  await page.screenshot({path:`${output}/05-mobile.png`,fullPage:true,animations:'disabled'})
  await page.goto(base+`/#/resumes/${meta.resumeId}`)
  await page.getByLabel('项目名称 *',{exact:true}).waitFor()
  check('390px校对页无横向溢出',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  await page.getByLabel('项目名称 *',{exact:true}).fill('尚未保存的修改')
  await page.locator('.main-links').getByRole('link',{name:'我的简历',exact:true}).click()
  await page.getByRole('button',{name:'继续编辑',exact:true}).click()
  check('未保存内容离开时有保护',page.url().includes(String(meta.resumeId)))
  await page.getByLabel('项目名称 *',{exact:true}).fill('前端全流程验证项目')
  await page.locator('.main-links').getByRole('link',{name:'我的简历',exact:true}).click()
  await page.waitForURL('**/#/resumes')
  await page.locator('.user-menu').click()
  await page.getByRole('menuitem',{name:'退出登录',exact:true}).click()
  await page.getByRole('button',{name:'退出',exact:true}).click()
  await page.waitForURL('**/#/login')
  const old=await context.request.get(base+'/api/users/me',{headers:{Authorization:`Bearer ${token}`}})
  check('页面退出后旧JWT失效',old.status()===401)
  check('无浏览器运行时异常',errors.length===0)
  meta.success=true
}catch(e){meta.success=false;meta.failure=String(e.message).replaceAll(password,'[redacted]');console.error(meta.failure);await page.screenshot({path:`${output}/failure.png`,fullPage:true,animations:'disabled'});process.exitCode=1}
finally{await writeFile(`${output}/report.json`,JSON.stringify(meta,null,2));await browser.close();console.log(`Report ${output}/report.json · ${checks.length} passed; test records retained`)}



